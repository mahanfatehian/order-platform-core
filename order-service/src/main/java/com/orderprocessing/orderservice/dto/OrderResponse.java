package com.orderprocessing.orderservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(description = "Order response payload")
public class OrderResponse {

    @Schema(description = "Order unique identifier")
    private UUID id;

    @Schema(description = "User who placed the order")
    private UUID userId;

    @Schema(description = "Current status of the order", example = "PENDING")
    private String status;

    @Schema(description = "Total calculated amount", example = "299.98")
    private BigDecimal totalAmount;

    @Schema(description = "List of items in the order")
    private List<OrderItemResponse> items;

    @Schema(description = "Order creation timestamp")
    private Instant createdAt;

    private Instant updatedAt;

    private String failureReason;

    private boolean cancellable;
}
