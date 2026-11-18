package com.orderprocessing.webui.dto;

import java.time.Instant;
import java.util.UUID;

public record StockReceiptView(
        UUID id,
        UUID productId,
        int quantity,
        String reason,
        String idempotencyKey,
        Instant createdAt
) {
}
