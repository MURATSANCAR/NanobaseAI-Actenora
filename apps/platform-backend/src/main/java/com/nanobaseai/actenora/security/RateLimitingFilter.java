package com.nanobaseai.actenora.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * FAZ 27 — simple per-client sliding window rate limiter (in-process).
 * Production deployments should front this with gateway/Redis limits.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final boolean enabled;
    private final int maxRequests;
    private final long windowSeconds;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitingFilter(
            @Value("${actenora.security.rate-limit.enabled:true}") boolean enabled,
            @Value("${actenora.security.rate-limit.max-requests:120}") int maxRequests,
            @Value("${actenora.security.rate-limit.window-seconds:60}") long windowSeconds
    ) {
        this.enabled = enabled;
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!enabled || isHealth(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        String key = clientKey(request);
        Instant now = Instant.now();
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || existing.expiresAt.isBefore(now)) {
                return new Window(now.plusSeconds(windowSeconds), new AtomicInteger(0));
            }
            return existing;
        });
        int count = window.count.incrementAndGet();
        if (count > maxRequests) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", Long.toString(windowSeconds));
            response.setContentType("application/problem+json");
            response.getWriter().write(
                    "{\"title\":\"RATE_LIMITED\",\"status\":429,\"detail\":\"Too many requests\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isHealth(String uri) {
        return uri != null && (
                uri.startsWith("/actuator/health")
                        || uri.startsWith("/actuator/prometheus")
                        || uri.startsWith("/actuator/info")
                        || uri.startsWith("/health"));
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        String tenant = request.getHeader("X-Actenora-Tenant-Id");
        String remote = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        return (tenant == null ? "" : tenant) + "|" + remote;
    }

    private record Window(Instant expiresAt, AtomicInteger count) {
    }
}
