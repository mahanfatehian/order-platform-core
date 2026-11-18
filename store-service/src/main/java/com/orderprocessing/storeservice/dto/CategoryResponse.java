package com.orderprocessing.storeservice.dto;

import com.orderprocessing.storeservice.model.ProductCategory;

import java.util.UUID;

public record CategoryResponse(UUID id, String name) {
    public static CategoryResponse from(ProductCategory category) {
        return new CategoryResponse(category.getId(), category.getName());
    }
}
