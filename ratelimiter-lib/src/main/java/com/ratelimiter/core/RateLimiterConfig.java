package com.ratelimiter.core;

import java.time.Duration;
import java.util.Objects;

/**
 * Describes the shape of a single client's token bucket: how many tokens it
 * can hold at most, and how fast it refills.
 *
 * @param capacity     maximum number of tokens the bucket can hold (also the
 *                     largest possible burst size)
 * @param refillTokens number of tokens added per {@code refillPeriod}
 * @param refillPeriod the period over which {@code refillTokens} are added,
 *                     e.g. {@code Duration.ofSeconds(1)} for a per-second rate
 */
public record RateLimiterConfig(long capacity, long refillTokens, Duration refillPeriod) {

    public RateLimiterConfig {
        Objects.requireNonNull(refillPeriod, "refillPeriod must not be null");
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive, got " + capacity);
        }
        if (refillTokens <= 0) {
            throw new IllegalArgumentException("refillTokens must be positive, got " + refillTokens);
        }
        if (refillPeriod.isZero() || refillPeriod.isNegative()) {
            throw new IllegalArgumentException("refillPeriod must be positive, got " + refillPeriod);
        }
    }

    /** Convenience factory for a simple "N requests per second" limit that starts full. */
    public static RateLimiterConfig perSecond(long requestsPerSecond) {
        return new RateLimiterConfig(requestsPerSecond, requestsPerSecond, Duration.ofSeconds(1));
    }

    /** Convenience factory for an explicit burst capacity plus a steady refill rate. */
    public static RateLimiterConfig of(long capacity, long refillTokens, Duration refillPeriod) {
        return new RateLimiterConfig(capacity, refillTokens, refillPeriod);
    }
}
