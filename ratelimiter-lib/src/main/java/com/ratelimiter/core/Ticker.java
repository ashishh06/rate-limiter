package com.ratelimiter.core;

/**
 * Abstraction over a monotonic nanosecond time source.
 *
 * <p>Injecting this instead of calling {@link System#nanoTime()} directly lets
 * tests advance time deterministically (see {@code FakeTicker} in the test
 * sources) instead of sleeping real wall-clock time to exercise refill logic.
 */
@FunctionalInterface
public interface Ticker {

    /**
     * The system ticker, backed by {@link System#nanoTime()}. Safe for
     * concurrent use; values are only meaningful relative to each other.
     */
    Ticker SYSTEM = System::nanoTime;

    long nanoTime();
}
