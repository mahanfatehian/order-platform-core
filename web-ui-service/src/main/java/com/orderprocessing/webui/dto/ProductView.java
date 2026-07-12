package com.orderprocessing.webui.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductView(
        UUID id,
        String name,
        String description,
        String sku,
        BigDecimal price,
        String category,
        boolean active,
        int availableQuantity,
        Instant createdAt,
        Instant updatedAt
) {
    public boolean inStock() { return active && availableQuantity > 0; }
}
