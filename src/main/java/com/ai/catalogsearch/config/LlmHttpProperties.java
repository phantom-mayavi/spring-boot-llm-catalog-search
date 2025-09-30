package com.ai.catalogsearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "llm.http")
public class LlmHttpProperties {
    
    private int connectTimeoutMs = 5000; // 5 seconds default
    private int readTimeoutMs = 30000; // 30 seconds default
    private boolean retryEnabled = true;
    
    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }
    
    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }
    
    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }
    
    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
    
    public boolean isRetryEnabled() {
        return retryEnabled;
    }
    
    public void setRetryEnabled(boolean retryEnabled) {
        this.retryEnabled = retryEnabled;
    }
}
