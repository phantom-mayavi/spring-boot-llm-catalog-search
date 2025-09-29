package com.ai.catalogsearch.dto;

import com.ai.catalogsearch.service.SearchService.ProductSearchResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {
    private List<ProductSearchResult> items;
    private int page;
    private int size;
    private long total;
    private boolean hasNext;
}
