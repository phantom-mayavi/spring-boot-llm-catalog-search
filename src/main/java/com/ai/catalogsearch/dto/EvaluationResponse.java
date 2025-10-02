package com.ai.catalogsearch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationResponse {
    private List<QueryResult> results;
    private double averagePrecision;
    private int k;
    private int totalQueries;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryResult {
        private String query;
        private List<String> expectedSkus;
        private List<String> actualSkus;
        private double precisionAtK;
        private int expectedCount;
        private int foundCount;
    }
}
