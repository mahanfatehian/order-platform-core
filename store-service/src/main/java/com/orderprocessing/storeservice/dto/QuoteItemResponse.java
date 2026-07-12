package com.orderprocessing.storeservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record QuoteItemResponse(
        UUID productId,
        String name,
        BigDecimal unitPrice,
        boolean active,
        int requestedQuantity,
        int availableQuantity,
        boolean available
) {
}
