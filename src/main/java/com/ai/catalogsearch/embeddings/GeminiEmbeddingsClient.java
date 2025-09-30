package com.ai.catalogsearch.embeddings;

import com.ai.catalogsearch.cache.LRUCache;
import com.ai.catalogsearch.config.LlmCacheProperties;
import com.ai.catalogsearch.config.LlmHttpProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GeminiEmbeddingsClient implements EmbeddingsClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LlmHttpProperties httpProperties;
    private final LRUCache<String, double[]> embeddingsCache;

    // Cache metrics
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);

    @Value("${gemini.embeddings.base-url}")
    private String baseUrl;

    @Value("${gemini.embeddings.api-key}")
    private String apiKey;

    @Value("${gemini.embeddings.model:text-embedding-004}")
    private String model;

    @Value("${gemini.embeddings.batch-size:100}")
    private int batchSize;

    public GeminiEmbeddingsClient(LlmHttpProperties httpProperties, LlmCacheProperties cacheProperties) {
        this.httpProperties = httpProperties;
        this.embeddingsCache = new LRUCache<>(cacheProperties.getSize());
        this.restTemplate = createConfiguredRestTemplate();
    }

    private RestTemplate createConfiguredRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(httpProperties.getConnectTimeoutMs());
        factory.setReadTimeout(httpProperties.getReadTimeoutMs());

        RestTemplate template = new RestTemplate();
        template.setRequestFactory(factory);
        return template;
    }

    @Override
    public double[] embed(String text) {
        // Generate cache key
        String cacheKey = generateCacheKey("gemini", model, text);
        
        // Check cache first
        double[] cached = embeddingsCache.get(cacheKey);
        if (cached != null) {
            long hits = cacheHits.incrementAndGet();
            log.debug("Cache hit for embedding. Total hits: {}, misses: {}", hits, cacheMisses.get());
            return cached;
        }
        
        long misses = cacheMisses.incrementAndGet();
        log.debug("Cache miss for embedding. Total hits: {}, misses: {}", cacheHits.get(), misses);
        
        // Get embedding with retry logic
        List<String> texts = List.of(text);
        List<double[]> embeddings = embedBatchWithRetry(texts);
        
        if (!embeddings.isEmpty()) {
            double[] result = embeddings.get(0);
            // Cache the result
            embeddingsCache.put(cacheKey, result);
            return result;
        }
        
        return new double[0]; // Return empty array on failure
    }

    @Override
    public List<double[]> embedBatch(List<String> texts) {
        if (texts.isEmpty()) {
            return new ArrayList<>();
        }

        List<double[]> allEmbeddings = new ArrayList<>();
        List<String> uncachedTexts = new ArrayList<>();
        List<Integer> uncachedIndices = new ArrayList<>();
        
        // Check cache for each text
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            String cacheKey = generateCacheKey("gemini", model, text);
            double[] cached = embeddingsCache.get(cacheKey);
            
            if (cached != null) {
                long hits = cacheHits.incrementAndGet();
                log.debug("Cache hit for batch embedding {}. Total hits: {}, misses: {}", i, hits, cacheMisses.get());
                allEmbeddings.add(cached);
            } else {
                long misses = cacheMisses.incrementAndGet();
                log.debug("Cache miss for batch embedding {}. Total hits: {}, misses: {}", i, cacheHits.get(), misses);
                uncachedTexts.add(text);
                uncachedIndices.add(i);
                allEmbeddings.add(null); // Placeholder
            }
        }
        
        // Process uncached texts
        if (!uncachedTexts.isEmpty()) {
            List<double[]> uncachedEmbeddings = embedBatchWithRetry(uncachedTexts);
            
            // Insert uncached results and cache them
            for (int i = 0; i < uncachedIndices.size(); i++) {
                int originalIndex = uncachedIndices.get(i);
                if (i < uncachedEmbeddings.size()) {
                    double[] embedding = uncachedEmbeddings.get(i);
                    allEmbeddings.set(originalIndex, embedding);
                    
                    // Cache the result
                    String cacheKey = generateCacheKey("gemini", model, uncachedTexts.get(i));
                    embeddingsCache.put(cacheKey, embedding);
                }
            }
        }
        
        // Remove any null entries (failed embeddings)
        return allEmbeddings.stream()
                .filter(embedding -> embedding != null && embedding.length > 0)
                .collect(Collectors.toList());
    }

    private List<double[]> processBatch(List<String> texts) {
        try {
            EmbeddingRequest request = createEmbeddingRequest(texts);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-goog-api-key", apiKey);

            HttpEntity<EmbeddingRequest> entity = new HttpEntity<>(request, headers);
            
            String url = baseUrl + "/v1/models/" + model + ":batchEmbedContents";
            
            log.debug("Sending embedding request for {} texts to {}", texts.size(), url);
            
            ResponseEntity<EmbeddingResponse> response = restTemplate.postForEntity(
                url, entity, EmbeddingResponse.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("Failed to get embeddings response");
            }

            return response.getBody().getEmbeddings().stream()
                    .map(embedding -> normalizeVector(embedding.getValues()))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error processing embeddings batch", e);
            throw new RuntimeException("Failed to process embeddings batch", e);
        }
    }

    private EmbeddingRequest createEmbeddingRequest(List<String> texts) {
        EmbeddingRequest request = new EmbeddingRequest();
        
        List<EmbeddingRequest.RequestItem> requests = texts.stream()
                .map(text -> {
                    EmbeddingRequest.RequestItem item = new EmbeddingRequest.RequestItem();
                    item.setModel("models/" + model);
                    
                    EmbeddingRequest.Content content = new EmbeddingRequest.Content();
                    EmbeddingRequest.Part part = new EmbeddingRequest.Part();
                    part.setText(text);
                    content.setParts(List.of(part));
                    item.setContent(content);
                    
                    return item;
                })
                .collect(Collectors.toList());

        request.setRequests(requests);
        return request;
    }

    private double[] normalizeVector(List<Double> vector) {
        double[] arr = vector.stream().mapToDouble(Double::doubleValue).toArray();
        
        // Calculate L2 norm
        double norm = 0.0;
        for (double value : arr) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);

        // Normalize if norm is not zero
        if (norm > 0) {
            for (int i = 0; i < arr.length; i++) {
                arr[i] = arr[i] / norm;
            }
        }

        return arr;
    }

    private String generateCacheKey(String provider, String model, String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = provider + ":" + model + ":" + text;
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            
            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            // Fallback to simple string concatenation (not recommended for production)
            return provider + ":" + model + ":" + text.hashCode();
        }
    }

    private List<double[]> embedBatchWithRetry(List<String> texts) {
        try {
            return processBatchInternal(texts);
        } catch (HttpServerErrorException e) {
            // Retry only for 5xx errors if retry is enabled
            if (httpProperties.isRetryEnabled() && e.getStatusCode().is5xxServerError()) {
                log.debug("5xx error occurred, retrying batch once: {}", e.getMessage());
                try {
                    return processBatchInternal(texts);
                } catch (Exception retryException) {
                    log.error("Retry failed for batch embedding API call: {}", retryException.getMessage(), retryException);
                    return new ArrayList<>();
                }
            } else {
                log.error("5xx error occurred but retry disabled: {}", e.getMessage(), e);
                return new ArrayList<>();
            }
        } catch (ResourceAccessException e) {
            // Retry for I/O timeouts if retry is enabled
            if (httpProperties.isRetryEnabled()) {
                log.debug("I/O timeout occurred, retrying batch once: {}", e.getMessage());
                try {
                    return processBatchInternal(texts);
                } catch (Exception retryException) {
                    log.error("Retry failed for batch embedding API call: {}", retryException.getMessage(), retryException);
                    return new ArrayList<>();
                }
            } else {
                log.error("I/O timeout occurred but retry disabled: {}", e.getMessage(), e);
                return new ArrayList<>();
            }
        } catch (Exception e) {
            log.error("Error calling Gemini batch API: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    private List<double[]> processBatchInternal(List<String> texts) {
        List<double[]> allEmbeddings = new ArrayList<>();
        
        // Process in batches to respect API limits
        for (int i = 0; i < texts.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, texts.size());
            List<String> batch = texts.subList(i, endIndex);
            
            List<double[]> batchEmbeddings = processBatch(batch);
            allEmbeddings.addAll(batchEmbeddings);
        }

        return allEmbeddings;
    }

    @Data
    public static class EmbeddingRequest {
        private List<RequestItem> requests;

        @Data
        public static class RequestItem {
            private String model;
            private Content content;
        }

        @Data
        public static class Content {
            private List<Part> parts;
        }

        @Data
        public static class Part {
            private String text;
        }
    }

    @Data
    public static class EmbeddingResponse {
        private List<EmbeddingResult> embeddings;

        @Data
        public static class EmbeddingResult {
            private List<Double> values;
        }
    }
}
