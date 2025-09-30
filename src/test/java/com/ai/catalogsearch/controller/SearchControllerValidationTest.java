package com.ai.catalogsearch.controller;

import com.ai.catalogsearch.dto.ErrorResponse;
import com.ai.catalogsearch.dto.SearchResponse;
import com.ai.catalogsearch.ratelimit.RateLimiter;
import com.ai.catalogsearch.service.SearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SearchController.class)
class SearchControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchService searchService;

    @MockBean
    private RateLimiter rateLimiter;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testEmptyQueryReturns400() throws Exception {
        when(rateLimiter.isAllowed(anyString())).thenReturn(true);

        String response = mockMvc.perform(get("/search").param("q", ""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/json"))
                .andReturn().getResponse().getContentAsString();

        ErrorResponse errorResponse = objectMapper.readValue(response, ErrorResponse.class);
        assertEquals("validation_error", errorResponse.getError());
        assertEquals("Query parameter 'q' is required and cannot be empty", errorResponse.getDetails());
        assertEquals(400, errorResponse.getStatus());
    }

    @Test
    void testPriceMinGreaterThanPriceMaxReturns400() throws Exception {
        when(rateLimiter.isAllowed(anyString())).thenReturn(true);

        String response = mockMvc.perform(get("/search")
                .param("q", "laptop")
                .param("priceMin", "1000")
                .param("priceMax", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/json"))
                .andReturn().getResponse().getContentAsString();

        ErrorResponse errorResponse = objectMapper.readValue(response, ErrorResponse.class);
        assertEquals("validation_error", errorResponse.getError());
        assertEquals("PriceMin cannot be greater than priceMax", errorResponse.getDetails());
        assertEquals(400, errorResponse.getStatus());
    }

    @Test
    void testInvalidSizeReturns400() throws Exception {
        when(rateLimiter.isAllowed(anyString())).thenReturn(true);

        String response = mockMvc.perform(get("/search")
                .param("q", "laptop")
                .param("size", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/json"))
                .andReturn().getResponse().getContentAsString();

        ErrorResponse errorResponse = objectMapper.readValue(response, ErrorResponse.class);
        assertEquals("validation_error", errorResponse.getError());
        assertEquals("Size parameter must be between 1 and 50", errorResponse.getDetails());
        assertEquals(400, errorResponse.getStatus());
    }

    @Test
    void testNegativePageReturns400() throws Exception {
        when(rateLimiter.isAllowed(anyString())).thenReturn(true);

        String response = mockMvc.perform(get("/search")
                .param("q", "laptop")
                .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/json"))
                .andReturn().getResponse().getContentAsString();

        ErrorResponse errorResponse = objectMapper.readValue(response, ErrorResponse.class);
        assertEquals("validation_error", errorResponse.getError());
        assertEquals("Page parameter must be non-negative", errorResponse.getDetails());
        assertEquals(400, errorResponse.getStatus());
    }

    @Test
    void testRateLimitExceededReturns429() throws Exception {
        when(rateLimiter.isAllowed(anyString())).thenReturn(false);

        String response = mockMvc.perform(get("/search").param("q", "laptop"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentType("application/json"))
                .andReturn().getResponse().getContentAsString();

        ErrorResponse errorResponse = objectMapper.readValue(response, ErrorResponse.class);
        assertEquals("rate_limited", errorResponse.getError());
        assertEquals("Rate limit exceeded. Maximum 30 requests per minute allowed.", errorResponse.getDetails());
        assertEquals(429, errorResponse.getStatus());
    }

    @Test
    void testValidRequestReturnsSuccess() throws Exception {
        when(rateLimiter.isAllowed(anyString())).thenReturn(true);
        SearchResponse mockResponse = new SearchResponse();
        mockResponse.setItems(Collections.emptyList());
        mockResponse.setTotal(0L);
        when(searchService.enhancedSemanticSearch(anyString(), any(), any(), any(), anyInt(), anyInt(), anyString()))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/search")
                .param("q", "laptop")
                .param("priceMin", "100")
                .param("priceMax", "1000")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"));
    }
}
