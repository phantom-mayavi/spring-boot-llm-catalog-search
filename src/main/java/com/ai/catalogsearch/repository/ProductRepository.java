package com.ai.catalogsearch.repository;

import com.ai.catalogsearch.model.Product;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class ProductRepository {
    
    private final ConcurrentHashMap<UUID, Product> products = new ConcurrentHashMap<>();
    
    public void save(Product product) {
        products.put(product.getId(), product);
    }
    
    public void saveAll(List<Product> productList) {
        productList.forEach(this::save);
    }
    
    public List<Product> findAll() {
        return new ArrayList<>(products.values());
    }
    
    public List<Product> findAll(int limit) {
        return products.values()
                .stream()
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    public Product findById(UUID id) {
        return products.get(id);
    }
    
    public List<Product> findByCategory(String category) {
        return products.values()
                .stream()
                .filter(product -> product.getCategory().equalsIgnoreCase(category))
                .collect(Collectors.toList());
    }
    
    public long count() {
        return products.size();
    }
    
    public void clear() {
        products.clear();
    }
}
