package com.ratelimiter.core;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * {@link RateLimiter} implementation using one {@link TokenBucket} per
 * client, keyed by client id (e.g. API key or IP address).
 *
 * <p>Buckets are created lazily on first use via
 * {@link ConcurrentHashMap#computeIfAbsent}, which guarantees exactly one
 * bucket is ever created per key even if many threads race to be the first
 * caller for a brand-new client. Different clients' buckets are fully
 * independent, so throughput scales across clients without any shared lock.
 *
 * <p>Each client's limit shape is resolved once, at bucket-creation time, via
 * a {@link Function}. This allows tiered limits (e.g. free vs. paid API keys)
 * without the caller needing to look anything up themselves.
 *
 * <p><b>Memory note:</b> buckets are never evicted in this version &mdash; a
 * long-running process that sees unbounded distinct client ids will grow
 * this map unboundedly. A production deployment would add a
 * size- or time-based eviction policy (e.g. Caffeine cache); left out here to
 * keep the core algorithm the focus. See the README for how this would be
 * extended.
 */
public final class TokenBucketRateLimiter implements RateLimiter {

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final Function<String, RateLimiterConfig> configResolver;
    private final Ticker ticker;

    /** Applies the same {@link RateLimiterConfig} to every client. */
    public TokenBucketRateLimiter(RateLimiterConfig config) {
        this(id -> config, Ticker.SYSTEM);
    }

    /** Resolves each client's config individually, e.g. by tier lookup. */
    public TokenBucketRateLimiter(Function<String, RateLimiterConfig> configResolver) {
        this(configResolver, Ticker.SYSTEM);
    }

    /** Full constructor, primarily for injecting a {@link Ticker} in tests. */
    TokenBucketRateLimiter(Function<String, RateLimiterConfig> configResolver, Ticker ticker) {
        this.configResolver = Objects.requireNonNull(configResolver, "configResolver");
        this.ticker = Objects.requireNonNull(ticker, "ticker");
    }

    @Override
    public boolean tryAcquire(String clientId) {
        return tryAcquire(clientId, 1);
    }

    @Override
    public boolean tryAcquire(String clientId, long permits) {
        Objects.requireNonNull(clientId, "clientId");
        return bucketFor(clientId).tryConsume(permits);
    }

    @Override
    public long availableTokens(String clientId) {
        Objects.requireNonNull(clientId, "clientId");
        TokenBucket existing = buckets.get(clientId);
        return existing == null ? configResolver.apply(clientId).capacity() : existing.availableTokens();
    }

    private TokenBucket bucketFor(String clientId) {
        return buckets.computeIfAbsent(clientId, id -> {
            RateLimiterConfig config = configResolver.apply(id);
            return new TokenBucket(config.capacity(), config.refillTokens(),
                    config.refillPeriod().toNanos(), ticker);
        });
    }
}
