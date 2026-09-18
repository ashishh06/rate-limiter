package com.ratelimiter.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

/**
 * Binds the {@code ratelimiter.tiers} section of application.yml into a
 * map of tier name -> limit shape, e.g.:
 *
 * <pre>
 * ratelimiter:
 *   tiers:
 *     free:
 *       capacity: 5
 *       refill-tokens: 5
 *       refill-period: 1s
 *     paid:
 *       capacity: 100
 *       refill-tokens: 100
 *       refill-period: 1s
 *   default-tier: free
 * </pre>
 *
 * Keeping limits in config rather than hardcoded means ops can retune a
 * tier's throughput without a code change or redeploy of the JAR.
 */
@ConfigurationProperties(prefix = "ratelimiter")
public class RateLimiterProperties {

    /** Tier name -> bucket shape for that tier. */
    private Map<String, Tier> tiers = Map.of();

    /** Tier applied to clients not found in {@link #clientTiers}. */
    private String defaultTier = "free";

    /** Explicit client-id -> tier-name overrides, e.g. a specific paid customer's API key. */
    private Map<String, String> clientTiers = Map.of();

    public Map<String, Tier> getTiers() {
        return tiers;
    }

    public void setTiers(Map<String, Tier> tiers) {
        this.tiers = tiers;
    }

    public String getDefaultTier() {
        return defaultTier;
    }

    public void setDefaultTier(String defaultTier) {
        this.defaultTier = defaultTier;
    }

    public Map<String, String> getClientTiers() {
        return clientTiers;
    }

    public void setClientTiers(Map<String, String> clientTiers) {
        this.clientTiers = clientTiers;
    }

    /** One tier's bucket shape, as plain mutable fields for property binding. */
    public static class Tier {
        private long capacity;
        private long refillTokens;
        private Duration refillPeriod = Duration.ofSeconds(1);

        public long getCapacity() {
            return capacity;
        }

        public void setCapacity(long capacity) {
            this.capacity = capacity;
        }

        public long getRefillTokens() {
            return refillTokens;
        }

        public void setRefillTokens(long refillTokens) {
            this.refillTokens = refillTokens;
        }

        public Duration getRefillPeriod() {
            return refillPeriod;
        }

        public void setRefillPeriod(Duration refillPeriod) {
            this.refillPeriod = refillPeriod;
        }
    }
}
