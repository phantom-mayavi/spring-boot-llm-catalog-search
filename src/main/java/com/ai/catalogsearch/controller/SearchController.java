package com.ai.catalogsearch.controller;

import com.ai.catalogsearch.dto.SearchResponse;
import com.ai.catalogsearch.exception.RateLimitException;
import com.ai.catalogsearch.exception.ValidationException;
import com.ai.catalogsearch.ratelimit.RateLimiter;
import com.ai.catalogsearch.service.SearchService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final RateLimiter rateLimiter;

    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(
            @RequestParam String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal priceMin,
            @RequestParam(required = false) BigDecimal priceMax,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "score") String sort,
            HttpServletRequest request) {
        
        // Rate limiting
        String clientId = getClientId(request);
        if (!rateLimiter.isAllowed(clientId)) {
            throw new RateLimitException("Rate limit exceeded. Maximum 30 requests per minute allowed.");
        }
        
        log.info("Enhanced search request - q: '{}', category: {}, priceMin: {}, priceMax: {}, page: {}, size: {}, sort: {}", 
                q, category, priceMin, priceMax, page, size, sort);
        
        // Validation
        validateSearchParameters(q, priceMin, priceMax, page, size, sort);
        
        SearchResponse results = searchService.enhancedSemanticSearch(
                q.trim(), category, priceMin, priceMax, page, size, sort);
        
        log.info("Enhanced search completed - returned {} items out of {} total", 
                results.getItems().size(), results.getTotal());
        
        return ResponseEntity.ok(results);
    }
    
    private void validateSearchParameters(String q, BigDecimal priceMin, BigDecimal priceMax, 
                                        int page, int size, String sort) {
        if (q == null || q.trim().isEmpty()) {
            throw new ValidationException("Query parameter 'q' is required and cannot be empty");
        }
        
        if (page < 0) {
            throw new ValidationException("Page parameter must be non-negative");
        }
        
        if (size < 1 || size > 50) {
            throw new ValidationException("Size parameter must be between 1 and 50");
        }
        
        if (!("score".equals(sort) || "price".equals(sort))) {
            throw new ValidationException("Sort parameter must be 'score' or 'price'");
        }
        
        if (priceMin != null && priceMin.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("PriceMin must be non-negative");
        }
        
        if (priceMax != null && priceMax.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("PriceMax must be non-negative");
        }
        
        if (priceMin != null && priceMax != null && priceMin.compareTo(priceMax) > 0) {
            throw new ValidationException("PriceMin cannot be greater than priceMax");
        }
    }
    
    private String getClientId(HttpServletRequest request) {
        // Try to get real IP from headers (for load balancers/proxies)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
