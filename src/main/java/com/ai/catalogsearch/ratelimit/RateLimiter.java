package com.ai.catalogsearch.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class RateLimiter {
    
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    // Default: 30 requests per minute
    private final int maxRequests = 30;
    private final long windowSizeSeconds = 60;
    
    public RateLimiter() {
        // Clean up expired entries every minute
        scheduler.scheduleAtFixedRate(this::cleanup, 1, 1, TimeUnit.MINUTES);
    }
    
    public boolean isAllowed(String clientId) {
        long now = Instant.now().getEpochSecond();
        long windowStart = now - (now % windowSizeSeconds);
        
        WindowCounter counter = counters.computeIfAbsent(clientId, k -> new WindowCounter());
        
        synchronized (counter) {
            // Reset counter if we're in a new window
            if (counter.windowStart != windowStart) {
                counter.windowStart = windowStart;
                counter.count.set(0);
            }
            
            int currentCount = counter.count.incrementAndGet();
            boolean allowed = currentCount <= maxRequests;
            
            if (!allowed) {
                log.debug("Rate limit exceeded for client: {} ({}/{} requests in current window)", 
                    clientId, currentCount, maxRequests);
            }
            
            return allowed;
        }
    }
    
    private void cleanup() {
        long now = Instant.now().getEpochSecond();
        long cutoff = now - windowSizeSeconds * 2; // Keep last 2 windows
        
        counters.entrySet().removeIf(entry -> entry.getValue().windowStart < cutoff);
        log.debug("Rate limiter cleanup completed. Active clients: {}", counters.size());
    }
    
    private static class WindowCounter {
        volatile long windowStart;
        final AtomicInteger count = new AtomicInteger(0);
    }
}
