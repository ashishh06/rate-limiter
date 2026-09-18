package com.ratelimiter.gateway;

import com.ratelimiter.core.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Applies rate limiting to every request before it reaches a controller.
 *
 * <p>Registered automatically by Spring Boot because it's a {@code @Component}
 * extending a servlet {@link jakarta.servlet.Filter}. On rejection, responds
 * with {@code 429 Too Many Requests} and a {@code Retry-After} header instead
 * of letting the request through - this is the actual enforcement point; the
 * algorithm itself doesn't know anything about HTTP.
 */
@Component
public class RateLimitFilter extends HttpFilter {

    private final RateLimiter rateLimiter;
    private final ClientIdentityResolver identityResolver;

    public RateLimitFilter(RateLimiter rateLimiter, ClientIdentityResolver identityResolver) {
        this.rateLimiter = rateLimiter;
        this.identityResolver = identityResolver;
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        String clientId = identityResolver.resolve(request);

        if (rateLimiter.tryAcquire(clientId)) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(rateLimiter.availableTokens(clientId)));
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(429); // HttpServletResponse has no named constant for 429 pre-Servlet-6.1
        response.setHeader("Retry-After", "1");
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"error\":\"rate_limit_exceeded\",\"message\":\"Too many requests for client '"
                        + clientId + "'. Try again shortly.\"}");
    }
}
