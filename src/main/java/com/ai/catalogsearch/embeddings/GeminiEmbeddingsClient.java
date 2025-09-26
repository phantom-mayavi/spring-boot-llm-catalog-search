package com.ai.catalogsearch.embeddings;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiEmbeddingsClient implements EmbeddingsClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.embeddings.base-url}")
    private String baseUrl;

    @Value("${gemini.embeddings.api-key}")
    private String apiKey;

    @Value("${gemini.embeddings.model:text-embedding-004}")
    private String model;

    @Value("${gemini.embeddings.batch-size:100}")
    private int batchSize;

    @Override
    public double[] embed(String text) {
        List<String> texts = List.of(text);
        List<double[]> embeddings = embedBatch(texts);
        return embeddings.get(0);
    }

    @Override
    public List<double[]> embedBatch(List<String> texts) {
        if (texts.isEmpty()) {
            return new ArrayList<>();
        }

        List<double[]> allEmbeddings = new ArrayList<>();
        
        // Process in batches to respect API limits
        for (int i = 0; i < texts.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, texts.size());
            List<String> batch = texts.subList(i, endIndex);
            
            try {
                List<double[]> batchEmbeddings = processBatch(batch);
                allEmbeddings.addAll(batchEmbeddings);
            } catch (Exception e) {
                log.error("Failed to process embeddings batch {}-{}", i, endIndex, e);
                throw new RuntimeException("Failed to generate embeddings", e);
            }
        }

        return allEmbeddings;
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

    @Data
    private static class EmbeddingRequest {
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
    private static class EmbeddingResponse {
        private List<EmbeddingResult> embeddings;

        @Data
        public static class EmbeddingResult {
            private List<Double> values;
        }
    }
}
