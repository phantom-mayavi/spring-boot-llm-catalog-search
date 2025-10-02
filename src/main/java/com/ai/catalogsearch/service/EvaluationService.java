package com.ai.catalogsearch.service;

import com.ai.catalogsearch.dto.EvaluationQuery;
import com.ai.catalogsearch.dto.EvaluationResponse;
import com.ai.catalogsearch.dto.SearchResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final SearchService searchService;
    private final ObjectMapper objectMapper;

    public EvaluationResponse runEvaluation(int k) {
        log.info("Running evaluation with k={}", k);
        
        // Load queries from eval/queries.json
        List<EvaluationQuery> queries = loadEvaluationQueries();
        log.info("Loaded {} evaluation queries", queries.size());
        
        // Run each query and compute precision@k
        List<EvaluationResponse.QueryResult> results = queries.stream()
                .map(query -> evaluateQuery(query, k))
                .collect(Collectors.toList());
        
        // Compute overall average precision
        double averagePrecision = results.stream()
                .mapToDouble(EvaluationResponse.QueryResult::getPrecisionAtK)
                .average()
                .orElse(0.0);
        
        log.info("Evaluation completed - Average precision@{}: {:.4f}", k, averagePrecision);
        
        // Log compact table for console view
        logCompactTable(results, k, averagePrecision);
        
        return new EvaluationResponse(results, averagePrecision, k, queries.size());
    }
    
    private List<EvaluationQuery> loadEvaluationQueries() {
        try {
            // Load from file system since eval/ is not in resources
            java.io.File file = new java.io.File("eval/queries.json");
            return objectMapper.readValue(file, new TypeReference<List<EvaluationQuery>>() {});
        } catch (IOException e) {
            log.error("Failed to load evaluation queries from eval/queries.json", e);
            throw new RuntimeException("Failed to load evaluation queries", e);
        }
    }
    
    private EvaluationResponse.QueryResult evaluateQuery(EvaluationQuery evaluationQuery, int k) {
        String query = evaluationQuery.getQuery();
        List<String> expectedSkus = evaluationQuery.getExpectedSkus();
        
        log.debug("Evaluating query: '{}' with expected SKUs: {}", query, expectedSkus);
        
        // Run search with the query (using page=0, size=k to get top k results)
        SearchResponse searchResponse = searchService.enhancedSemanticSearch(
                query, null, null, null, 0, k, "score");
        
        // Extract SKUs from search results
        List<String> actualSkus = searchResponse.getItems().stream()
                .map(item -> item.getSku())
                .collect(Collectors.toList());
        
        log.debug("Query '{}' returned SKUs: {}", query, actualSkus);
        
        // Count how many expected SKUs are in the top k results
        int foundCount = (int) expectedSkus.stream()
                .mapToInt(expectedSku -> actualSkus.contains(expectedSku) ? 1 : 0)
                .sum();
        
        // Calculate precision@k = foundCount / min(k, expectedCount)
        int expectedCount = expectedSkus.size();
        double precisionAtK = expectedCount > 0 ? (double) foundCount / Math.min(k, expectedCount) : 0.0;
        
        log.debug("Query '{}' precision@{}: {:.4f} ({}/{} found)", 
                query, k, precisionAtK, foundCount, expectedCount);
        
        return new EvaluationResponse.QueryResult(
                query, expectedSkus, actualSkus, precisionAtK, expectedCount, foundCount);
    }
    
    private void logCompactTable(List<EvaluationResponse.QueryResult> results, int k, double averagePrecision) {
        log.info("\n" + "=".repeat(80));
        log.info("EVALUATION RESULTS (k={})", k);
        log.info("=".repeat(80));
        log.info(String.format("%-25s %-10s %-10s %-12s", "Query", "Expected", "Found", "Precision@" + k));
        log.info("-".repeat(80));
        
        for (EvaluationResponse.QueryResult result : results) {
            log.info(String.format("%-25s %-10d %-10d %.4f", 
                    truncate(result.getQuery(), 24),
                    result.getExpectedCount(),
                    result.getFoundCount(),
                    result.getPrecisionAtK()));
        }
        
        log.info("-".repeat(80));
        log.info(String.format("%-25s %-10s %-10s %.4f", "AVERAGE", "", "", averagePrecision));
        log.info("=".repeat(80));
    }
    
    private String truncate(String text, int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength - 3) + "..." : text;
    }
}
