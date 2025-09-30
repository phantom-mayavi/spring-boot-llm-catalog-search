package com.ai.catalogsearch.cache;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LRUCacheTest {

    @Test
    void testBasicPutAndGet() {
        LRUCache<String, String> cache = new LRUCache<>(3);
        
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        
        assertEquals("value1", cache.get("key1"));
        assertEquals("value2", cache.get("key2"));
        assertNull(cache.get("key3"));
    }

    @Test
    void testLRUEviction() {
        LRUCache<String, String> cache = new LRUCache<>(2);
        
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3"); // Should evict key1
        
        assertNull(cache.get("key1")); // Should be evicted
        assertEquals("value2", cache.get("key2"));
        assertEquals("value3", cache.get("key3"));
    }

    @Test
    void testAccessUpdatesLRU() {
        LRUCache<String, String> cache = new LRUCache<>(2);
        
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.get("key1"); // Access key1 to make it most recent
        cache.put("key3", "value3"); // Should evict key2, not key1
        
        assertEquals("value1", cache.get("key1")); // Should still be there
        assertNull(cache.get("key2")); // Should be evicted
        assertEquals("value3", cache.get("key3"));
    }

    @Test
    void testSize() {
        LRUCache<String, String> cache = new LRUCache<>(3);
        
        assertEquals(0, cache.size());
        
        cache.put("key1", "value1");
        assertEquals(1, cache.size());
        
        cache.put("key2", "value2");
        cache.put("key3", "value3");
        assertEquals(3, cache.size());
        
        cache.put("key4", "value4"); // Should not increase size beyond max
        assertEquals(3, cache.size());
    }

    @Test
    void testClear() {
        LRUCache<String, String> cache = new LRUCache<>(3);
        
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        assertEquals(2, cache.size());
        
        cache.clear();
        assertEquals(0, cache.size());
        assertNull(cache.get("key1"));
        assertNull(cache.get("key2"));
    }
}
