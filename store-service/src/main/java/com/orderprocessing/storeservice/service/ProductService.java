package com.orderprocessing.storeservice.service;

import com.orderprocessing.storeservice.model.Product;
import com.orderprocessing.storeservice.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Product getProductById(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with ID: " + id));
    }

    @Transactional
    public Product createProduct(Product product) {
        // Ensure a new UUID is generated if not provided
        if (product.getId() == null) {
            product.setId(UUID.randomUUID());
        }
        
        // Set timestamps
        Instant now = Instant.now();
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        
        return productRepository.save(product);
    }

    @Transactional
    public Product updateProduct(Product product) {
        // Verify product exists
        Product existingProduct = getProductById(product.getId());
        
        // Update timestamps
        product.setUpdatedAt(Instant.now());
        
        // Preserve created date
        product.setCreatedAt(existingProduct.getCreatedAt());
        
        return productRepository.save(product);
    }

    @Transactional
    public void deleteProduct(UUID id) {
        if (!productRepository.existsById(id)) {
            throw new RuntimeException("Product not found with ID: " + id);
        }
        productRepository.deleteById(id);
    }
}