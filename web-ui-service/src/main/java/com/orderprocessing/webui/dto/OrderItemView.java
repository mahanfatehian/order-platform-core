package com.orderprocessing.webui.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemView(
        UUID id,
        UUID productId,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal
) {
    public BigDecimal effectiveLineTotal() {
        return lineTotal != null ? lineTotal : unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
