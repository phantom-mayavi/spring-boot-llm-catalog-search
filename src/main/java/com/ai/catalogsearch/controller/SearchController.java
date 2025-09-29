package com.ai.catalogsearch.controller;

import com.ai.catalogsearch.dto.SearchResponse;
import com.ai.catalogsearch.service.SearchService;
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

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal priceMin,
            @RequestParam(required = false) BigDecimal priceMax,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "score") String sort) {
        
        log.info("Enhanced search request - q: '{}', category: {}, priceMin: {}, priceMax: {}, page: {}, size: {}, sort: {}", 
                q, category, priceMin, priceMax, page, size, sort);
        
        // Validation
        if (q == null || q.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Query parameter 'q' is required and cannot be empty");
        }
        
        if (page < 0) {
            return ResponseEntity.badRequest().body("Page parameter must be non-negative");
        }
        
        if (size <= 0) {
            return ResponseEntity.badRequest().body("Size parameter must be positive");
        }
        
        if (size > 50) {
            size = 50; // Cap at 50
        }
        
        if (!("score".equals(sort) || "price".equals(sort))) {
            return ResponseEntity.badRequest().body("Sort parameter must be 'score' or 'price'");
        }
        
        if (priceMin != null && priceMin.compareTo(BigDecimal.ZERO) < 0) {
            return ResponseEntity.badRequest().body("PriceMin must be non-negative");
        }
        
        if (priceMax != null && priceMax.compareTo(BigDecimal.ZERO) < 0) {
            return ResponseEntity.badRequest().body("PriceMax must be non-negative");
        }
        
        if (priceMin != null && priceMax != null && priceMin.compareTo(priceMax) > 0) {
            return ResponseEntity.badRequest().body("PriceMin cannot be greater than priceMax");
        }
        
        try {
            SearchResponse results = searchService.enhancedSemanticSearch(
                    q.trim(), category, priceMin, priceMax, page, size, sort);
            
            log.info("Enhanced search completed - returned {} items out of {} total", 
                    results.getItems().size(), results.getTotal());
            
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error during enhanced search", e);
            return ResponseEntity.internalServerError().body("An error occurred during search");
        }
    }
}
