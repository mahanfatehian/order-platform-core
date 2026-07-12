package com.orderprocessing.orderservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record StoreQuoteItemResponse(UUID productId, String name, BigDecimal unitPrice, boolean active,
                                     int requestedQuantity, int availableQuantity, boolean available) {
}
