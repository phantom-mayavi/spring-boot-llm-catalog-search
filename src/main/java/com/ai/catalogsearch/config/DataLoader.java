package com.ai.catalogsearch.config;

import com.ai.catalogsearch.model.Product;
import com.ai.catalogsearch.repository.ProductRepository;
import com.ai.catalogsearch.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final EmbeddingService embeddingService;

    @Override
    public void run(String... args) throws Exception {
        loadProductsFromCsv();
        generateEmbeddings();
    }

    private void loadProductsFromCsv() {
        try {
            ClassPathResource resource = new ClassPathResource("data/products.csv");
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                
                String line;
                boolean isFirstLine = true;
                int loadedCount = 0;
                
                while ((line = reader.readLine()) != null) {
                    // Skip header line
                    if (isFirstLine) {
                        isFirstLine = false;
                        continue;
                    }
                    
                    Product product = parseCsvLine(line);
                    if (product != null) {
                        productRepository.save(product);
                        loadedCount++;
                    }
                }
                
                log.info("Successfully loaded {} products from CSV into repository", loadedCount);
                
            }
        } catch (Exception e) {
            log.error("Failed to load products from CSV", e);
            throw new RuntimeException("Failed to load product data", e);
        }
    }

    private void generateEmbeddings() {
        try {
            log.info("Starting to generate embeddings for all products...");
            
            // Get all products from repository
            List<Product> allProducts = productRepository.findAll();
            
            if (allProducts.isEmpty()) {
                log.warn("No products found in repository, skipping embedding generation");
                return;
            }
            
            // Generate embeddings for all products
            embeddingService.generateAndStoreEmbeddings(allProducts);
            
            log.info("Successfully generated embeddings for {} products", 
                    embeddingService.getEmbeddingCount());
            
        } catch (Exception e) {
            log.error("Failed to generate embeddings during startup", e);
            // Don't throw exception to allow application to start even if embeddings fail
            log.warn("Application will continue without embeddings");
        }
    }

    private Product parseCsvLine(String line) {
        try {
            // Handle CSV parsing with quoted fields
            String[] fields = parseCSVLine(line);
            
            if (fields.length != 7) {
                log.warn("Invalid CSV line format: {}", line);
                return null;
            }

            UUID id = UUID.fromString(fields[0]);
            String sku = fields[1];
            String title = cleanQuotes(fields[2]);
            String description = cleanQuotes(fields[3]);
            String category = fields[4];
            BigDecimal price = new BigDecimal(fields[5]);
            List<String> tags = Arrays.asList(cleanQuotes(fields[6]).split(","));

            return Product.builder()
                    .id(id)
                    .sku(sku)
                    .title(title)
                    .description(description)
                    .category(category)
                    .price(price)
                    .tags(tags)
                    .build();

        } catch (Exception e) {
            log.error("Error parsing CSV line: {}", line, e);
            return null;
        }
    }

    private String[] parseCSVLine(String line) {
        // Simple CSV parser that handles quoted fields
        String[] result = new String[7];
        boolean inQuotes = false;
        StringBuilder currentField = new StringBuilder();
        int fieldIndex = 0;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result[fieldIndex++] = currentField.toString();
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        
        // Add the last field
        if (fieldIndex < 7) {
            result[fieldIndex] = currentField.toString();
        }
        
        return result;
    }

    private String cleanQuotes(String value) {
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
