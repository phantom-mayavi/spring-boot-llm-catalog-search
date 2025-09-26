package com.ai.catalogsearch.controller;

import com.ai.catalogsearch.model.Product;
import com.ai.catalogsearch.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository productRepository;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getProducts(
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        
        log.info("Fetching products with limit: {}", limit);
        
        // Validate limit parameter
        if (limit <= 0) {
            limit = 10;
        }
        if (limit > 100) {
            limit = 100; // Cap at 100 to prevent excessive responses
        }
        
        List<Product> products = productRepository.findAll(limit);
        long totalCount = productRepository.count();
        
        Map<String, Object> response = new HashMap<>();
        response.put("products", products);
        response.put("limit", limit);
        response.put("returned", products.size());
        response.put("total", totalCount);
        
        log.info("Returning {} products out of {} total", products.size(), totalCount);
        
        return ResponseEntity.ok(response);
    }
}
