package com.orderprocessing.storeservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record StockReceiptRequest(
        @NotNull @Positive Integer quantity,
        @NotBlank @Size(max = 500) String reason,
        @NotBlank @Size(max = 200) String idempotencyKey
) {
}
