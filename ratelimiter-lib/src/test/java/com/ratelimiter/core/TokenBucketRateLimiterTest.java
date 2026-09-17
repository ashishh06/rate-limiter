package com.ratelimiter.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Single-threaded correctness tests: bucket starts full, empties exactly at
 * capacity, refills at the configured rate, and never exceeds capacity no
 * matter how long it sits idle.
 */
class TokenBucketRateLimiterTest {

    @Test
    void startsFullAndAllowsExactlyCapacityRequests() {
        FakeTicker ticker = new FakeTicker();
        RateLimiterConfig config = RateLimiterConfig.of(5, 5, Duration.ofSeconds(1));
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(id -> config, ticker);

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("client-a"), "request " + i + " should be allowed within capacity");
        }
        assertFalse(limiter.tryAcquire("client-a"), "6th request should be rejected, bucket is empty");
    }

    @Test
    void refillsAtConfiguredRateAfterTimeAdvances() {
        FakeTicker ticker = new FakeTicker();
        // 10 tokens/sec, burst capacity 10.
        RateLimiterConfig config = RateLimiterConfig.of(10, 10, Duration.ofSeconds(1));
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(id -> config, ticker);

        // Drain the bucket completely.
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryAcquire("client-a"));
        }
        assertFalse(limiter.tryAcquire("client-a"));

        // Advance exactly 500ms -> expect exactly 5 tokens back.
        ticker.advance(500, TimeUnit.MILLISECONDS);
        assertEquals(5, limiter.availableTokens("client-a"));
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("client-a"), "should have exactly 5 tokens after 500ms");
        }
        assertFalse(limiter.tryAcquire("client-a"));
    }

    @Test
    void refillNeverExceedsCapacityEvenAfterLongIdlePeriod() {
        FakeTicker ticker = new FakeTicker();
        RateLimiterConfig config = RateLimiterConfig.of(10, 10, Duration.ofSeconds(1));
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(id -> config, ticker);

        // Sit idle for a very long time - refill must clamp at capacity, not overflow past it.
        ticker.advance(365, TimeUnit.DAYS);

        assertEquals(10, limiter.availableTokens("client-a"), "idle bucket must clamp at capacity");
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryAcquire("client-a"));
        }
        assertFalse(limiter.tryAcquire("client-a"), "must not have accrued more than capacity");
    }

    @Test
    void differentClientsHaveIndependentBuckets() {
        FakeTicker ticker = new FakeTicker();
        RateLimiterConfig config = RateLimiterConfig.of(1, 1, Duration.ofSeconds(1));
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(id -> config, ticker);

        assertTrue(limiter.tryAcquire("client-a"));
        assertFalse(limiter.tryAcquire("client-a"), "client-a is now out of tokens");
        assertTrue(limiter.tryAcquire("client-b"), "client-b's bucket is unaffected by client-a's usage");
    }

    @Test
    void perClientConfigResolverAppliesDifferentTiers() {
        FakeTicker ticker = new FakeTicker();
        RateLimiterConfig freeTier = RateLimiterConfig.of(2, 2, Duration.ofSeconds(1));
        RateLimiterConfig paidTier = RateLimiterConfig.of(20, 20, Duration.ofSeconds(1));
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(
                id -> id.startsWith("paid-") ? paidTier : freeTier, ticker);

        assertEquals(2, limiter.availableTokens("free-client"));
        assertEquals(20, limiter.availableTokens("paid-client"));
    }

    @Test
    void tryAcquireWithMultiplePermitsIsAllOrNothing() {
        FakeTicker ticker = new FakeTicker();
        RateLimiterConfig config = RateLimiterConfig.of(10, 10, Duration.ofSeconds(1));
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(id -> config, ticker);

        assertFalse(limiter.tryAcquire("client-a", 11), "request larger than capacity must fail");
        assertEquals(10, limiter.availableTokens("client-a"), "failed request must not partially consume tokens");

        assertTrue(limiter.tryAcquire("client-a", 7));
        assertEquals(3, limiter.availableTokens("client-a"));
        assertFalse(limiter.tryAcquire("client-a", 4), "not enough remaining for this batch");
        assertEquals(3, limiter.availableTokens("client-a"), "failed batch must not consume the 3 remaining tokens");
    }
}
