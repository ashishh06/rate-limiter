package com.ratelimiter.gateway;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Extracts a stable client identifier from an incoming request, used as the
 * bucket key by {@link RateLimitFilter}.
 *
 * <p>Prefers the {@code X-API-Key} header (the intended production case:
 * every client has an issued key). Falls back to the caller's remote
 * address so unauthenticated/anonymous traffic still gets a (shared, coarse)
 * limit instead of bypassing rate limiting entirely.
 */
@Component
public class ClientIdentityResolver {

    private static final String API_KEY_HEADER = "X-API-Key";

    public String resolve(HttpServletRequest request) {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey != null && !apiKey.isBlank()) {
            return "key:" + apiKey.trim();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
