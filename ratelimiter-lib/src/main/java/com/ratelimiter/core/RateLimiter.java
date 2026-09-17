package com.ratelimiter.core;

/**
 * A per-client rate limiter.
 *
 * <p>Implementations must be safe for concurrent use: multiple threads may
 * call {@link #tryAcquire} for the same or different {@code clientId} values
 * at the same time without over-admitting requests for any single client.
 *
 * <p>The interface is deliberately algorithm-agnostic so alternative
 * strategies (sliding window, leaky bucket, ...) can be dropped in later
 * without touching callers &mdash; see {@link TokenBucketRateLimiter} for the
 * only implementation currently provided.
 */
public interface RateLimiter {

    /**
     * Attempts to consume a single permit for {@code clientId}.
     *
     * @return {@code true} if the request is allowed, {@code false} if the
     *         client has exceeded its configured rate and the request should
     *         be rejected (e.g. with HTTP 429).
     */
    boolean tryAcquire(String clientId);

    /**
     * Attempts to consume {@code permits} permits for {@code clientId} as a
     * single atomic operation: either all permits are granted, or none are.
     *
     * @param permits number of permits to consume, must be positive
     */
    boolean tryAcquire(String clientId, long permits);

    /**
     * Returns the number of permits currently available for {@code clientId},
     * after applying any refill owed since the last access. Returns the
     * client's full starting capacity if the client has not been seen yet.
     *
     * <p>Intended for diagnostics and for populating headers like
     * {@code X-RateLimit-Remaining}; callers should not use it to predict the
     * outcome of a subsequent {@link #tryAcquire} call, since another thread
     * may consume permits in between.
     */
    long availableTokens(String clientId);
}
