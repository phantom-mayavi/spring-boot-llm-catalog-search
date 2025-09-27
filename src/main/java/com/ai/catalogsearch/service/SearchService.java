package com.ai.catalogsearch.service;

import com.ai.catalogsearch.embeddings.EmbeddingsClient;
import com.ai.catalogsearch.model.Product;
import com.ai.catalogsearch.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
