package com.orderprocessing.orderservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Schema(description = "Individual item within an order request")
public class OrderItemRequest {

    @NotNull(message = "Product ID is required")
    @Schema(description = "UUID of the product", example = "a1111111-1111-1111-1111-111111111111")
    private UUID productId;

    @NotBlank(message = "Product name is required")
    @Schema(description = "Name of the product for order history", example = "Wireless Headphones")
    private String productName;

    @NotNull(message = "Unit price is required")
    @Min(value = 0, message = "Unit price must be zero or greater")
    @Schema(description = "Price per unit", example = "149.99")
    private BigDecimal unitPrice;

    @Min(value = 1, message = "Quantity must be at least 1")
    @Schema(description = "Quantity of the product", example = "2")
    private int quantity;
}