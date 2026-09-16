package com.ratelimiter.core;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A single client's token bucket: holds up to {@code capacity} tokens,
 * refilling continuously at {@code refillTokens / refillPeriodNanos}.
 *
 * <p><b>Concurrency design.</b> A single bucket is expected to be hit by many
 * threads at once (all requests from one API key), so this class avoids a
 * mutex entirely and instead uses two {@link AtomicLong} fields updated via
 * compare-and-swap (CAS) retry loops:
 * <ul>
 *   <li>{@code availableScaled} &mdash; tokens currently in the bucket</li>
 *   <li>{@code lastRefillNanos} &mdash; timestamp of the last refill</li>
 * </ul>
 * Refill amounts would be fractional in general (e.g. 0.37 tokens after 37ms
 * at 10 tokens/sec), so token counts are stored as fixed-point integers
 * scaled by {@link #SCALE} rather than as {@code double}s. This keeps the CAS
 * loop's compare-and-swap exact and avoids floating point drift accumulating
 * over millions of refills, at the cost of a small amount of head-room lost
 * to truncation (bounded by 1/{@code SCALE} of a token per refill).
 */
final class TokenBucket {

    /** Fixed-point scale factor: token counts are stored as tokens * SCALE. */
    private static final long SCALE = 1_000_000L;

    private final long capacityScaled;
    private final long refillTokens;
    private final long refillPeriodNanos;
    private final Ticker ticker;

    private final AtomicLong availableScaled;
    private final AtomicLong lastRefillNanos;

    TokenBucket(long capacity, long refillTokens, long refillPeriodNanos, Ticker ticker) {
        this.capacityScaled = clampedScale(capacity);
        this.refillTokens = refillTokens;
        this.refillPeriodNanos = refillPeriodNanos;
        this.ticker = ticker;
        this.availableScaled = new AtomicLong(this.capacityScaled);
        this.lastRefillNanos = new AtomicLong(ticker.nanoTime());
    }

    /**
     * Attempts to atomically consume {@code permits} tokens. Refills the
     * bucket for elapsed time first, then either commits the withdrawal via
     * CAS or reports failure without side effects.
     */
    boolean tryConsume(long permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be positive, got " + permits);
        }
        long permitsScaled = permits * SCALE;

        while (true) {
            refill();
            long current = availableScaled.get();
            if (current < permitsScaled) {
                return false;
            }
            if (availableScaled.compareAndSet(current, current - permitsScaled)) {
                return true;
            }
            // Another thread mutated availableScaled between get() and
            // compareAndSet() (lost the race) - re-read and retry. Under high
            // contention this loop may spin a few times, but each iteration
            // does O(1) work and no thread ever blocks another.
        }
    }

    /** Returns current token count (post-refill), rounded down to whole tokens. */
    long availableTokens() {
        refill();
        return availableScaled.get() / SCALE;
    }

    /**
     * Adds tokens owed since the last refill, capped at capacity. Safe to
     * call from multiple threads concurrently: if two threads race to record
     * the refill, exactly one wins the CAS on {@code lastRefillNanos} and
     * applies the token credit; the loser simply skips this round; since
     * {@code tryConsume} always calls refill() immediately before checking
     * balance, the loser will pick up any owed tokens on its own next call.
     */
    private void refill() {
        long now = ticker.nanoTime();
        long last = lastRefillNanos.get();
        long elapsedNanos = now - last;
        if (elapsedNanos <= 0) {
            return;
        }

        long tokensToAddScaled = mulDiv(elapsedNanos, refillTokens * SCALE, refillPeriodNanos);
        if (tokensToAddScaled <= 0) {
            return;
        }

        if (lastRefillNanos.compareAndSet(last, now)) {
            availableScaled.updateAndGet(v -> Math.min(capacityScaled, v + tokensToAddScaled));
        }
        // else: another thread already advanced lastRefillNanos past `last`;
        // let its refill stand and don't double-credit.
    }

    /** Scales a token capacity by {@link #SCALE}, clamping to Long.MAX_VALUE instead of overflowing on absurd configs. */
    private static long clampedScale(long capacity) {
        try {
            return Math.multiplyExact(capacity, SCALE);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    /** Computes {@code (a * b) / c} using 128-bit intermediate precision to avoid overflow. */
    private static long mulDiv(long a, long b, long c) {
        long hi = Math.multiplyHigh(a, b);
        long lo = a * b;
        if (hi == 0 && lo >= 0) {
            return lo / c; // fast path: no overflow, avoid the BigInteger cost
        }
        return java.math.BigInteger.valueOf(a)
                .multiply(java.math.BigInteger.valueOf(b))
                .divide(java.math.BigInteger.valueOf(c))
                .longValueExact();
    }
}
