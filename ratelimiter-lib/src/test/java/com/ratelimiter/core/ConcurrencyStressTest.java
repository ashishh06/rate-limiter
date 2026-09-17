package com.ratelimiter.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves the lock-free CAS design in {@link TokenBucket} does not
 * over-admit requests when many threads hammer the same bucket at once.
 *
 * <p>The correctness property under test: for a bucket with a fixed capacity
 * and a frozen clock (no refill occurring mid-test), the number of
 * successful {@code tryAcquire} calls across ALL threads must equal exactly
 * the bucket's capacity - no more (which would mean the limiter is unsafe
 * and lets clients exceed their quota) and no less (which would mean the
 * limiter is losing valid grants to spurious CAS failures).
 */
class ConcurrencyStressTest {

    @Test
    @Timeout(30)
    void exactlyCapacityRequestsSucceedUnderHighContention() throws InterruptedException {
        final long capacity = 1_000;
        final int threadCount = 64;
        final int attemptsPerThread = 200; // 12,800 total attempts against 1,000 tokens

        FakeTicker ticker = new FakeTicker(); // frozen: isolates the "no over-admission" property from refill
        RateLimiterConfig config = RateLimiterConfig.of(capacity, capacity, Duration.ofSeconds(1));
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(id -> config, ticker);

        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threadCount);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        try {
            for (int t = 0; t < threadCount; t++) {
                pool.submit(() -> {
                    try {
                        startGate.await(); // all threads start hammering the bucket at the same instant
                        for (int i = 0; i < attemptsPerThread; i++) {
                            if (limiter.tryAcquire("shared-client")) {
                                successCount.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneGate.countDown();
                    }
                });
            }

            startGate.countDown(); // release all threads simultaneously
            boolean finished = doneGate.await(20, TimeUnit.SECONDS);
            assertEquals(true, finished, "all threads should complete within timeout");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(capacity, successCount.get(),
                "exactly `capacity` requests must succeed across all threads combined - "
                        + "more would mean over-admission (a correctness bug), fewer would mean "
                        + "the CAS loop is dropping valid grants under contention");
    }

    @Test
    @Timeout(30)
    void manyDistinctClientsDoNotInterfereWithEachOther() throws InterruptedException {
        final int clientCount = 200;
        final long perClientCapacity = 50;
        final int threadsPerClient = 10; // 2,000 threads total, contending only within their own client

        FakeTicker ticker = new FakeTicker();
        RateLimiterConfig config = RateLimiterConfig.of(perClientCapacity, perClientCapacity, Duration.ofSeconds(1));
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(id -> config, ticker);

        List<AtomicInteger> successCounts = new ArrayList<>();
        for (int c = 0; c < clientCount; c++) {
            successCounts.add(new AtomicInteger(0));
        }

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(clientCount * threadsPerClient);
        ExecutorService pool = Executors.newFixedThreadPool(64);

        try {
            for (int c = 0; c < clientCount; c++) {
                String clientId = "client-" + c;
                AtomicInteger counter = successCounts.get(c);
                for (int t = 0; t < threadsPerClient; t++) {
                    pool.submit(() -> {
                        try {
                            startGate.await();
                            // Each client's threads attempt more than its capacity, split across threads.
                            for (int i = 0; i < (perClientCapacity / threadsPerClient) + 5; i++) {
                                if (limiter.tryAcquire(clientId)) {
                                    counter.incrementAndGet();
                                }
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            doneGate.countDown();
                        }
                    });
                }
            }

            startGate.countDown();
            doneGate.await(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        for (int c = 0; c < clientCount; c++) {
            assertEquals(perClientCapacity, successCounts.get(c).get(),
                    "client-" + c + " should get exactly its own capacity, unaffected by other clients' load");
        }
    }
}
