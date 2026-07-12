package com.orderprocessing.webui.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record QuoteItemView(
        UUID productId,
        String name,
        BigDecimal unitPrice,
        boolean active,
        int requestedQuantity,
        int availableQuantity,
        boolean available
) {
    public BigDecimal subtotal() { return unitPrice.multiply(BigDecimal.valueOf(requestedQuantity)); }
}
