package com.ai.catalogsearch.service;

import com.ai.catalogsearch.dto.SearchResponse;
import com.ai.catalogsearch.embeddings.EmbeddingsClient;
import com.ai.catalogsearch.model.Product;
import com.ai.catalogsearch.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Comparator;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {
    
    private final EmbeddingsClient embeddingsClient;
    private final ProductRepository productRepository;
    private final EmbeddingService embeddingService;
    
    public List<ProductSearchResult> searchProducts(String query, int limit) {
        log.info("Searching for products with query: '{}', limit: {}", query, limit);
        
        // Embed the query
        double[] queryEmbedding = embeddingsClient.embed(query);
        log.debug("Generated query embedding with dimension: {}", queryEmbedding.length);
        
        // Get all products and compute similarity scores
        List<Product> allProducts = productRepository.findAll();
        
        List<ProductSearchResult> results = allProducts.stream()
                .map(product -> {
                    Optional<double[]> productEmbedding = embeddingService.getEmbedding(product.getId());
                    if (productEmbedding.isPresent()) {
                        double similarity = cosineSimilarity(queryEmbedding, productEmbedding.get());
                        return new ProductSearchResult(product, similarity);
                    }
                    return null;
                })
                .filter(result -> result != null) // Only products with embeddings
                .sorted(Comparator.comparingDouble(ProductSearchResult::getSimilarityScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
        
        log.info("Found {} matching products for query: '{}'", results.size(), query);
        return results;
    }

    public SearchResponse enhancedSemanticSearch(String query, String category, BigDecimal priceMin, 
                                               BigDecimal priceMax, int page, int size, String sort) {
        log.info("Enhanced search - query: '{}', category: {}, priceMin: {}, priceMax: {}, page: {}, size: {}, sort: {}", 
                query, category, priceMin, priceMax, page, size, sort);
        
        // Step 1: Run vector search first (get all results, not limited)
        double[] queryEmbedding = embeddingsClient.embed(query);
        List<Product> allProducts = productRepository.findAll();
        
        List<ProductSearchResult> vectorResults = allProducts.stream()
                .map(product -> {
                    Optional<double[]> productEmbedding = embeddingService.getEmbedding(product.getId());
                    if (productEmbedding.isPresent()) {
                        double similarity = cosineSimilarity(queryEmbedding, productEmbedding.get());
                        return new ProductSearchResult(product, similarity);
                    }
                    return null;
                })
                .filter(result -> result != null)
                .sorted(Comparator.comparingDouble(ProductSearchResult::getSimilarityScore).reversed())
                .collect(Collectors.toList());
        
        // Step 2: Apply filters
        List<ProductSearchResult> filteredResults = vectorResults.stream()
                .filter(result -> {
                    Product product = result.getProduct();
                    
                    // Category filter
                    if (category != null && !category.trim().isEmpty() && 
                        !product.getCategory().equalsIgnoreCase(category.trim())) {
                        return false;
                    }
                    
                    // Price range filters
                    BigDecimal productPrice = product.getPrice();
                    if (priceMin != null && productPrice.compareTo(priceMin) < 0) {
                        return false;
                    }
                    if (priceMax != null && productPrice.compareTo(priceMax) > 0) {
                        return false;
                    }
                    
                    return true;
                })
                .collect(Collectors.toList());
        
        // Step 3: Re-rank/sort
        if ("price".equals(sort)) {
            filteredResults = filteredResults.stream()
                    .sorted(Comparator.comparing(result -> result.getProduct().getPrice()))
                    .collect(Collectors.toList());
        }
        // Default is already sorted by score desc from vector search
        
        // Step 4: Apply pagination
        long total = filteredResults.size();
        int startIndex = page * size;
        int endIndex = Math.min(startIndex + size, (int) total);
        
        List<ProductSearchResult> pageItems;
        if (startIndex >= total) {
            pageItems = List.of(); // Empty list if page is beyond results
        } else {
            pageItems = filteredResults.subList(startIndex, endIndex);
        }
        
        boolean hasNext = endIndex < total;
        
        log.info("Enhanced search results - total: {}, page: {}, size: {}, returned: {}, hasNext: {}", 
                total, page, size, pageItems.size(), hasNext);
        
        return new SearchResponse(pageItems, page, size, total, hasNext);
    }
    
    private double cosineSimilarity(double[] vectorA, double[] vectorB) {
        if (vectorA.length != vectorB.length) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        
        double dotProduct = 0.0;
        double magnitudeA = 0.0;
        double magnitudeB = 0.0;
        
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            magnitudeA += vectorA[i] * vectorA[i];
            magnitudeB += vectorB[i] * vectorB[i];
        }
        
        magnitudeA = Math.sqrt(magnitudeA);
        magnitudeB = Math.sqrt(magnitudeB);
        
        if (magnitudeA == 0.0 || magnitudeB == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (magnitudeA * magnitudeB);
    }
    
    // Inner class to hold search results with similarity scores
    public static class ProductSearchResult {
        private final Product product;
        private final double similarityScore;
        
        public ProductSearchResult(Product product, double similarityScore) {
            this.product = product;
            this.similarityScore = similarityScore;
        }
        
        public Product getProduct() {
            return product;
        }
        
        public double getSimilarityScore() {
            return similarityScore;
        }
        
        // Delegate all product methods for easy access
        public UUID getId() {
            return product.getId();
        }
        
        public String getSku() {
            return product.getSku();
        }
        
        public String getTitle() {
            return product.getTitle();
        }
        
        public String getDescription() {
            return product.getDescription();
        }
        
        public String getCategory() {
            return product.getCategory();
        }
        
        public java.math.BigDecimal getPrice() {
            return product.getPrice();
        }
        
        public List<String> getTags() {
            return product.getTags();
        }
    }
}
