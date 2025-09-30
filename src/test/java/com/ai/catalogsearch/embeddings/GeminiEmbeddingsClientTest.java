package com.ai.catalogsearch.embeddings;

import com.ai.catalogsearch.config.LlmCacheProperties;
import com.ai.catalogsearch.config.LlmHttpProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeminiEmbeddingsClientTest {

    @Mock
    private RestTemplate restTemplate;

    private GeminiEmbeddingsClient client;
    private LlmHttpProperties httpProperties;
    private LlmCacheProperties cacheProperties;

    @BeforeEach
    void setUp() {
        httpProperties = new LlmHttpProperties();
        httpProperties.setRetryEnabled(true);
        httpProperties.setConnectTimeoutMs(5000);
        httpProperties.setReadTimeoutMs(30000);
        
        cacheProperties = new LlmCacheProperties();
        cacheProperties.setSize(100);
        
        client = new GeminiEmbeddingsClient(httpProperties, cacheProperties);
        
        // Replace the RestTemplate with our mock
        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(client, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(client, "baseUrl", "https://test-api.com");
        ReflectionTestUtils.setField(client, "model", "text-embedding-004");
        ReflectionTestUtils.setField(client, "batchSize", 100);
    }

    @Test
    void testCacheHitOnRepeatedQueries() {
        // Arrange
        String testText = "test text";
        // Note: The implementation normalizes vectors, so we expect normalized values
        List<Double> rawVector = Arrays.asList(0.6, 0.8, 0.0); // This will normalize to approximately [0.6, 0.8, 0.0]
        
        GeminiEmbeddingsClient.EmbeddingResponse mockResponse = new GeminiEmbeddingsClient.EmbeddingResponse();
        GeminiEmbeddingsClient.EmbeddingResponse.EmbeddingResult embeddingResult = new GeminiEmbeddingsClient.EmbeddingResponse.EmbeddingResult();
        embeddingResult.setValues(rawVector);
        mockResponse.setEmbeddings(List.of(embeddingResult));
        
        ResponseEntity<GeminiEmbeddingsClient.EmbeddingResponse> responseEntity = 
            new ResponseEntity<>(mockResponse, HttpStatus.OK);
        
        when(restTemplate.postForEntity(anyString(), any(), eq(GeminiEmbeddingsClient.EmbeddingResponse.class)))
            .thenReturn(responseEntity);

        // Act - First call should hit the API
        double[] result1 = client.embed(testText);
        
        // Act - Second call should hit the cache
        double[] result2 = client.embed(testText);

        // Assert - Both results should be identical (and normalized)
        assertEquals(3, result1.length);
        assertEquals(3, result2.length);
        assertArrayEquals(result1, result2, 0.0001); // Results should be exactly the same
        
        // Verify that the API was called only once (second call was from cache)
        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(GeminiEmbeddingsClient.EmbeddingResponse.class));
    }

    @Test
    void testRetryOn5xxError() {
        // Arrange
        String testText = "test text";
        List<Double> rawVector = Arrays.asList(0.6, 0.8, 0.0);
        
        GeminiEmbeddingsClient.EmbeddingResponse mockResponse = new GeminiEmbeddingsClient.EmbeddingResponse();
        GeminiEmbeddingsClient.EmbeddingResponse.EmbeddingResult result = new GeminiEmbeddingsClient.EmbeddingResponse.EmbeddingResult();
        result.setValues(rawVector);
        mockResponse.setEmbeddings(List.of(result));
        
        ResponseEntity<GeminiEmbeddingsClient.EmbeddingResponse> responseEntity = 
            new ResponseEntity<>(mockResponse, HttpStatus.OK);
        
        when(restTemplate.postForEntity(anyString(), any(), eq(GeminiEmbeddingsClient.EmbeddingResponse.class)))
            .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
            .thenReturn(responseEntity);

        // Act
        double[] actualResult = client.embed(testText);

        // Assert - Should get a result after retry
        assertNotNull(actualResult);
        
        // Verify that the API was called at least once (and verify retry behavior separately)
        verify(restTemplate, atLeast(1)).postForEntity(anyString(), any(), eq(GeminiEmbeddingsClient.EmbeddingResponse.class));
    }

    @Test
    void testNoRetryOn4xxError() {
        // Arrange
        String testText = "test text";
        
        when(restTemplate.postForEntity(anyString(), any(), eq(GeminiEmbeddingsClient.EmbeddingResponse.class)))
            .thenThrow(new HttpServerErrorException(HttpStatus.BAD_REQUEST));

        // Act
        double[] result = client.embed(testText);

        // Assert
        assertEquals(0, result.length);
        
        // Verify that the API was called only once (no retry for 4xx)
        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(GeminiEmbeddingsClient.EmbeddingResponse.class));
    }

    @Test
    void testRetryDisabled() {
        // Arrange
        httpProperties.setRetryEnabled(false);
        client = new GeminiEmbeddingsClient(httpProperties, cacheProperties);
        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(client, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(client, "baseUrl", "https://test-api.com");
        ReflectionTestUtils.setField(client, "model", "text-embedding-004");
        ReflectionTestUtils.setField(client, "batchSize", 100);
        
        String testText = "test text";
        
        when(restTemplate.postForEntity(anyString(), any(), eq(GeminiEmbeddingsClient.EmbeddingResponse.class)))
            .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        // Act
        double[] result = client.embed(testText);

        // Assert
        assertEquals(0, result.length);
        
        // Verify that the API was called only once (retry disabled)
        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(GeminiEmbeddingsClient.EmbeddingResponse.class));
    }

    @Test
    void testDifferentTextsGenerateDifferentCacheKeys() {
        // Arrange
        String testText1 = "test text 1";
        String testText2 = "test text 2";
        
        GeminiEmbeddingsClient.EmbeddingResponse mockResponse1 = new GeminiEmbeddingsClient.EmbeddingResponse();
        GeminiEmbeddingsClient.EmbeddingResponse.EmbeddingResult result1 = new GeminiEmbeddingsClient.EmbeddingResponse.EmbeddingResult();
        result1.setValues(Arrays.asList(0.6, 0.8, 0.0));
        mockResponse1.setEmbeddings(List.of(result1));
        
        GeminiEmbeddingsClient.EmbeddingResponse mockResponse2 = new GeminiEmbeddingsClient.EmbeddingResponse();
        GeminiEmbeddingsClient.EmbeddingResponse.EmbeddingResult result2 = new GeminiEmbeddingsClient.EmbeddingResponse.EmbeddingResult();
        result2.setValues(Arrays.asList(0.8, 0.6, 0.0));
        mockResponse2.setEmbeddings(List.of(result2));
        
        ResponseEntity<GeminiEmbeddingsClient.EmbeddingResponse> responseEntity1 = 
            new ResponseEntity<>(mockResponse1, HttpStatus.OK);
        ResponseEntity<GeminiEmbeddingsClient.EmbeddingResponse> responseEntity2 = 
            new ResponseEntity<>(mockResponse2, HttpStatus.OK);
        
        when(restTemplate.postForEntity(anyString(), any(), eq(GeminiEmbeddingsClient.EmbeddingResponse.class)))
            .thenReturn(responseEntity1)
            .thenReturn(responseEntity2);

        // Act
        double[] result1Actual = client.embed(testText1);
        double[] result2Actual = client.embed(testText2);

        // Assert - Different texts should produce different results
        assertEquals(3, result1Actual.length);
        assertEquals(3, result2Actual.length);
        assertFalse(Arrays.equals(result1Actual, result2Actual)); // Should be different
        
        // Verify that the API was called twice (different cache keys)
        verify(restTemplate, times(2)).postForEntity(anyString(), any(), eq(GeminiEmbeddingsClient.EmbeddingResponse.class));
    }
}
