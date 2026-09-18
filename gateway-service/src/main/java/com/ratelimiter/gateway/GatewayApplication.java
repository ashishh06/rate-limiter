package com.ratelimiter.gateway;

import com.ratelimiter.core.RateLimiter;
import com.ratelimiter.core.RateLimiterConfig;
import com.ratelimiter.core.TokenBucketRateLimiter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Map;

@SpringBootApplication
@EnableConfigurationProperties(RateLimiterProperties.class)
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    /**
     * Builds the shared {@link RateLimiter} bean. The config-resolver function
     * looks up each client's tier (explicit override, else the configured
     * default tier) and converts it to a {@link RateLimiterConfig}. This
     * runs once per client, the first time that client is seen -
     * {@link TokenBucketRateLimiter} caches the resulting bucket after that.
     */
    @Bean
    public RateLimiter rateLimiter(RateLimiterProperties properties) {
        Map<String, RateLimiterProperties.Tier> tiers = properties.getTiers();
        Map<String, String> clientTiers = properties.getClientTiers();
        String defaultTierName = properties.getDefaultTier();

        if (!tiers.containsKey(defaultTierName)) {
            throw new IllegalStateException(
                    "ratelimiter.default-tier '" + defaultTierName
                            + "' has no matching entry under ratelimiter.tiers");
        }

        return new TokenBucketRateLimiter(clientId -> {
            String tierName = clientTiers.getOrDefault(clientId, defaultTierName);
            RateLimiterProperties.Tier tier = tiers.getOrDefault(tierName, tiers.get(defaultTierName));
            return RateLimiterConfig.of(tier.getCapacity(), tier.getRefillTokens(), tier.getRefillPeriod());
        });
    }
}
