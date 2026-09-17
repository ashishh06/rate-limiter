package com.ratelimiter.core;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A {@link Ticker} whose clock only moves when a test explicitly advances it.
 * This lets refill logic be tested exactly ("after precisely 500ms, exactly
 * 5 tokens should have refilled at 10/sec") without sleeping real time and
 * without tests becoming flaky under CI load.
 */
final class FakeTicker implements Ticker {

    private final AtomicLong nanos = new AtomicLong(0L);

    @Override
    public long nanoTime() {
        return nanos.get();
    }

    void advance(long amount, TimeUnit unit) {
        nanos.addAndGet(unit.toNanos(amount));
    }
}
