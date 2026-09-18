package com.ratelimiter.gateway;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Stand-ins for "real" downstream services. In an actual gateway these
 * would be proxied calls to other systems; here they exist purely so
 * {@link RateLimitFilter} has something to protect, keeping the demo
 * self-contained with no external dependencies to run.
 */
@RestController
public class DummyBackendController {

    @GetMapping("/api/ping")
    public Map<String, Object> ping() {
        return Map.of("message", "pong", "timestamp", Instant.now().toString());
    }

    @GetMapping("/api/data")
    public Map<String, Object> data() {
        return Map.of(
                "message", "here is some simulated data",
                "timestamp", Instant.now().toString());
    }
}
