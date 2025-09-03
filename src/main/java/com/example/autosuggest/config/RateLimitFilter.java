package com.example.autosuggest.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Order(20)
public class RateLimitFilter extends OncePerRequestFilter {

    private final boolean enabled;
    private final int capacity;
    private final Duration window;
    private final Map<String, WindowCounter> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${suggest.ratelimit.enabled:false}") boolean enabled,
            @Value("${suggest.ratelimit.capacity:50}") int capacity,
            @Value("${suggest.ratelimit.window:PT1S}") Duration window) {
        this.enabled = enabled;
        this.capacity = capacity;
        this.window = window;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) return true;
        // Only rate-limit the main suggest endpoint
        return !("GET".equalsIgnoreCase(request.getMethod()) && "/suggest".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String key = resolveKey(request);
        WindowCounter counter = windows.computeIfAbsent(key, k -> new WindowCounter(System.currentTimeMillis()));
        long now = System.currentTimeMillis();
        synchronized (counter) {
            if (now - counter.startMs >= window.toMillis()) {
                counter.startMs = now;
                counter.count.set(0);
            }
            int c = counter.count.incrementAndGet();
            if (c > capacity) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded\"}");
                return;
            }
            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(Math.max(0, capacity - c)));
        }
        filterChain.doFilter(request, response);
    }

    private String resolveKey(HttpServletRequest request) {
        String client = request.getHeader("X-Client-Id");
        if (client != null && !client.isBlank()) return "cid:" + client;
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return "xff:" + xff.split(",")[0].trim();
        return "ip:" + request.getRemoteAddr();
    }

    private static class WindowCounter {
        volatile long startMs;
        AtomicInteger count = new AtomicInteger();
        WindowCounter(long startMs) { this.startMs = startMs; }
    }
}
