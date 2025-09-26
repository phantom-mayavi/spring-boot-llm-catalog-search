package com.ai.catalogsearch.embeddings;

import java.util.List;

public interface EmbeddingsClient {
    
    /**
     * Generate embedding vector for a single text
     * @param text The text to embed
     * @return Normalized embedding vector
     */
    double[] embed(String text);
    
    /**
     * Generate embedding vectors for multiple texts in batch
     * @param texts List of texts to embed
     * @return List of normalized embedding vectors
     */
    List<double[]> embedBatch(List<String> texts);
}
