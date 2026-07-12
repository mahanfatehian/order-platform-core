package com.orderprocessing.orderservice.dto;

import java.util.UUID;

public record StoreQuoteItemRequest(UUID productId, int quantity) {
}
