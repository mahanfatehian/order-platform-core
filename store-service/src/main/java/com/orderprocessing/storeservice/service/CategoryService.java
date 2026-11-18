package com.orderprocessing.storeservice.service;

import com.orderprocessing.storeservice.exception.CatalogRuleException;
import com.orderprocessing.storeservice.model.ProductCategory;
import com.orderprocessing.storeservice.repository.ProductCategoryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CategoryService {
    private final ProductCategoryRepository categories;

    public CategoryService(ProductCategoryRepository categories) {
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<ProductCategory> list() {
        return categories.findAllByOrderByNameAsc();
    }

    @Transactional
    public ProductCategory create(String name) {
        String trimmed = requireName(name);
        return categories.findByNameIgnoreCase(trimmed).orElseGet(() -> insert(trimmed));
    }

    private ProductCategory insert(String name) {
        ProductCategory category = new ProductCategory();
        category.setId(UUID.randomUUID());
        category.setName(name);
        category.setCreatedAt(Instant.now());
        try {
            return categories.saveAndFlush(category);
        } catch (DataIntegrityViolationException ex) {
            return categories.findByNameIgnoreCase(name).orElseThrow(() -> ex);
        }
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new CatalogRuleException(HttpStatus.BAD_REQUEST, "CATEGORY_NAME_REQUIRED", "Category name is required");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 255) {
            throw new CatalogRuleException(HttpStatus.BAD_REQUEST, "CATEGORY_NAME_TOO_LONG",
                    "Category name cannot exceed 255 characters");
        }
        return trimmed;
    }
}
