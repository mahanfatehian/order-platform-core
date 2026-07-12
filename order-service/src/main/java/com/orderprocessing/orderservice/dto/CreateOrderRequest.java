package com.orderprocessing.orderservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Schema(description = "Request payload for creating a new order")
public class CreateOrderRequest {

    @NotEmpty(message = "Order must contain at least one item")
    @Size(max = 100, message = "An order cannot contain more than 100 products")
    @Valid
    @Schema(description = "List of items to include in the order")
    private List<OrderItemRequest> items;
}
