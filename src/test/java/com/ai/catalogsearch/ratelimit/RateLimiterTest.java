package com.ai.catalogsearch.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    @Test
    void testRateLimiterAllowsRequestsUnderLimit() {
        RateLimiter rateLimiter = new RateLimiter();
        String clientId = "test-client";

        // Should allow first 30 requests
        for (int i = 0; i < 30; i++) {
            assertTrue(rateLimiter.isAllowed(clientId), 
                "Request " + (i + 1) + " should be allowed");
        }

        // 31st request should be denied
        assertFalse(rateLimiter.isAllowed(clientId), 
            "31st request should be denied");
    }

    @Test
    void testRateLimiterIsolatesClients() {
        RateLimiter rateLimiter = new RateLimiter();
        String client1 = "client1";
        String client2 = "client2";

        // Fill up client1's quota
        for (int i = 0; i < 30; i++) {
            assertTrue(rateLimiter.isAllowed(client1));
        }
        assertFalse(rateLimiter.isAllowed(client1));

        // Client2 should still be allowed
        assertTrue(rateLimiter.isAllowed(client2));
    }
}
