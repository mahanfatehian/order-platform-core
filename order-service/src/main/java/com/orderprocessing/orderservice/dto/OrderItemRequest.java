package com.orderprocessing.orderservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Schema(description = "Individual item within an order request")
public class OrderItemRequest {

    @NotNull(message = "Product ID is required")
    @Schema(description = "UUID of the product", example = "a1111111-1111-1111-1111-111111111111")
    private UUID productId;

    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 10000, message = "Quantity cannot exceed 10000")
    @Schema(description = "Quantity of the product", example = "2")
    private int quantity;
}
