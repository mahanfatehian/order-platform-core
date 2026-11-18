package com.orderprocessing.storeservice.dto;

import com.orderprocessing.storeservice.model.StockReceipt;

import java.time.Instant;
import java.util.UUID;

public record StockReceiptResponse(
        UUID id,
        UUID productId,
        int quantity,
        String reason,
        String idempotencyKey,
        Instant createdAt
) {
    public static StockReceiptResponse from(StockReceipt receipt) {
        return new StockReceiptResponse(receipt.getId(), receipt.getProductId(), receipt.getQuantity(),
                receipt.getReason(), receipt.getIdempotencyKey(), receipt.getCreatedAt());
    }
}
