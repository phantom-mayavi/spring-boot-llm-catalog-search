package com.ai.catalogsearch.service;

import com.ai.catalogsearch.embeddings.EmbeddingsClient;
import com.ai.catalogsearch.model.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingsClient embeddingsClient;
    private final Map<UUID, double[]> productEmbeddings = new ConcurrentHashMap<>();

    /**
     * Generate and store embeddings for a list of products
     */
    public void generateAndStoreEmbeddings(List<Product> products) {
        if (products.isEmpty()) {
            log.info("No products to generate embeddings for");
            return;
        }

        log.info("Starting embedding generation for {} products", products.size());
        
        try {
            // Create text inputs by combining title and description
            List<String> texts = products.stream()
                    .map(this::createEmbeddingText)
                    .collect(Collectors.toList());

            // Generate embeddings in batch
            List<double[]> embeddings = embeddingsClient.embedBatch(texts);

            // Store embeddings in memory map
            for (int i = 0; i < products.size(); i++) {
                Product product = products.get(i);
                double[] embedding = embeddings.get(i);
                
                // Convert double[] to float[] for memory efficiency
                float[] floatEmbedding = convertToFloatArray(embedding);
                productEmbeddings.put(product.getId(), convertToDoubleArray(floatEmbedding));
                
                log.debug("Generated embedding for product: {} (dimension: {})", 
                         product.getId(), embedding.length);
            }

            log.info("Successfully generated and stored embeddings for {} products", products.size());
            
        } catch (Exception e) {
            log.error("Failed to generate embeddings for products", e);
            throw new RuntimeException("Failed to generate product embeddings", e);
        }
    }

    /**
     * Get embedding for a specific product
     */
    public Optional<double[]> getEmbedding(UUID productId) {
        double[] embedding = productEmbeddings.get(productId);
        return Optional.ofNullable(embedding);
    }

    /**
     * Get sample embeddings with limit
     */
    public Map<UUID, double[]> getSampleEmbeddings(int limit) {
        return productEmbeddings.entrySet().stream()
                .limit(limit)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    /**
     * Get total count of stored embeddings
     */
    public int getEmbeddingCount() {
        return productEmbeddings.size();
    }

    /**
     * Clear all stored embeddings
     */
    public void clearEmbeddings() {
        productEmbeddings.clear();
        log.info("Cleared all stored embeddings");
    }

    /**
     * Create embedding text by combining product title and description
     */
    private String createEmbeddingText(Product product) {
        StringBuilder text = new StringBuilder();
        
        if (product.getTitle() != null && !product.getTitle().trim().isEmpty()) {
            text.append(product.getTitle().trim());
        }
        
        if (product.getDescription() != null && !product.getDescription().trim().isEmpty()) {
            if (text.length() > 0) {
                text.append(" ");
            }
            text.append(product.getDescription().trim());
        }
        
        return text.toString();
    }

    /**
     * Convert double array to float array for memory efficiency
     */
    private float[] convertToFloatArray(double[] doubleArray) {
        float[] floatArray = new float[doubleArray.length];
        for (int i = 0; i < doubleArray.length; i++) {
            floatArray[i] = (float) doubleArray[i];
        }
        return floatArray;
    }

    /**
     * Convert float array back to double array
     */
    private double[] convertToDoubleArray(float[] floatArray) {
        double[] doubleArray = new double[floatArray.length];
        for (int i = 0; i < floatArray.length; i++) {
            doubleArray[i] = floatArray[i];
        }
        return doubleArray;
    }
}
