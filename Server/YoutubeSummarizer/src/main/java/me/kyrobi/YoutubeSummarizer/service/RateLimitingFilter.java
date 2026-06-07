package me.kyrobi.YoutubeSummarizer.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/*
Doesn't need to be created in any class. Spring automatically uses it.
All requests are automatically passed through this check
 */
@Component
@Order(1)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final Map<String, BucketEntry> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, jakarta.servlet.FilterChain chain)
            throws java.io.IOException, jakarta.servlet.ServletException {

        String ip = request.getHeader("CF-Connecting-IP");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }

        BucketEntry entry = buckets.computeIfAbsent(ip, k -> new BucketEntry(createBucket()));
        entry.lastAccess = System.currentTimeMillis();
        Bucket bucket = entry.bucket;

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Rate limit exceeded. Try again later.\"}");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/summarize");
    }

    private static Bucket createBucket() {
        return Bucket.builder()
            .addLimit(Bandwidth.classic(4, Refill.greedy(4, Duration.ofMinutes(1)))) // 4 requests a minute
            .addLimit(Bandwidth.classic(30, Refill.greedy(30, Duration.ofDays(1)))) // 20 requests a day
            .addLimit(Bandwidth.classic(100, Refill.greedy(100, Duration.ofDays(7)))) // 100 requests a week
            .build();
    }

    // Cool annotations to automatically make schedulers
    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    public void evictStaleBuckets() {
        long now = System.currentTimeMillis();
        long staleThreshold = Duration.ofMinutes(30).toMillis();
        buckets.values().removeIf(e -> now - e.lastAccess > staleThreshold);
    }

    private static class BucketEntry {
        final Bucket bucket;
        volatile long lastAccess;

        BucketEntry(Bucket bucket) {
            this.bucket = bucket;
            this.lastAccess = System.currentTimeMillis();
        }
    }
}
