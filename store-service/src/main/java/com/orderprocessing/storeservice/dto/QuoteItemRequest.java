package com.orderprocessing.storeservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record QuoteItemRequest(
        @NotNull(message = "Product ID is required") UUID productId,
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 10000, message = "Quantity cannot exceed 10000") int quantity
) {
}
