package com.ai.catalogsearch.controller;

import com.ai.catalogsearch.model.Product;
import com.ai.catalogsearch.repository.ProductRepository;
import com.ai.catalogsearch.service.EmbeddingService;
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
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository productRepository;
    private final EmbeddingService embeddingService;

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

    @GetMapping("/embeddings")
    public ResponseEntity<Map<String, Object>> getProductEmbeddings(
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        
        log.info("Fetching product embeddings with limit: {}", limit);
        
        // Validate limit parameter
        if (limit <= 0) {
            limit = 5;
        }
        if (limit > 50) {
            limit = 50; // Cap at 50 to prevent excessive responses
        }
        
        Map<UUID, double[]> sampleEmbeddings = embeddingService.getSampleEmbeddings(limit);
        int totalEmbeddings = embeddingService.getEmbeddingCount();
        
        // Format embeddings for response
        Map<String, Object> formattedEmbeddings = new HashMap<>();
        sampleEmbeddings.forEach((productId, embedding) -> {
            Map<String, Object> embeddingInfo = new HashMap<>();
            embeddingInfo.put("productId", productId.toString());
            embeddingInfo.put("dimension", embedding.length);
            embeddingInfo.put("vector", embedding);
            
            // Optionally include first few values for preview
            double[] preview = new double[Math.min(10, embedding.length)];
            System.arraycopy(embedding, 0, preview, 0, preview.length);
            embeddingInfo.put("preview", preview);
            
            formattedEmbeddings.put(productId.toString(), embeddingInfo);
        });
        
        Map<String, Object> response = new HashMap<>();
        response.put("embeddings", formattedEmbeddings);
        response.put("limit", limit);
        response.put("returned", sampleEmbeddings.size());
        response.put("total", totalEmbeddings);
        
        log.info("Returning {} embeddings out of {} total", sampleEmbeddings.size(), totalEmbeddings);
        
        return ResponseEntity.ok(response);
    }
}
