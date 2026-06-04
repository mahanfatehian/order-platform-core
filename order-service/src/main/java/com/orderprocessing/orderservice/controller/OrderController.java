package com.orderprocessing.orderservice.controller;

import com.orderprocessing.orderservice.dto.CreateOrderRequest;
import com.orderprocessing.orderservice.dto.OrderItemResponse;
import com.orderprocessing.orderservice.dto.OrderResponse;
import com.orderprocessing.orderservice.model.Order;
import com.orderprocessing.orderservice.model.OrderItem;
import com.orderprocessing.orderservice.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order management and processing endpoints")
@SecurityRequirement(name = "bearerAuth") // Applies JWT auth to all endpoints in this controller
public class OrderController {

    private final OrderService orderService;


    @Operation(summary = "Get all orders", description = "Retrieves a list of all orders in the system.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orders retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<OrderResponse> getAllOrders() {
        return orderService.getAllOrders().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Operation(summary = "Get my orders", description = "Retrieves all orders placed by the currently authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orders retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/my-orders")
    public List<OrderResponse> getMyOrders(@AuthenticationPrincipal Jwt jwt) {
        // Extract userId from the JWT claims (set by AuthService)
        UUID userId = UUID.fromString(jwt.getClaimAsString("userId"));

        return orderService.getOrdersByUserId(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Operation(summary = "Place a new order", description = "Synchronously reserves inventory and creates an order with PENDING status.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "409", description = "Insufficient inventory for one or more items")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@AuthenticationPrincipal Jwt jwt,
                                     @Valid @RequestBody CreateOrderRequest request) {

        // Extract userId from the JWT claims (set by AuthService)
        UUID userId = UUID.fromString(jwt.getClaimAsString("userId"));

        // Map DTOs to Entities
        List<OrderItem> items = request.getItems().stream().map(dto -> {
            OrderItem item = new OrderItem();
            item.setId(UUID.randomUUID());
            item.setProductId(dto.getProductId());
            item.setProductName(dto.getProductName());
            item.setUnitPrice(dto.getUnitPrice());
            item.setQuantity(dto.getQuantity());
            item.setCreatedAt(Instant.now());
            item.setUpdatedAt(Instant.now());
            return item;
        }).collect(Collectors.toList());

        Order createdOrder = orderService.createOrder(userId, items);
        return mapToResponse(createdOrder);
    }

    @Operation(summary = "Get order by ID", description = "Retrieves a specific order including its items.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order found"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    @GetMapping("/{id}")
    public OrderResponse getOrderById(@PathVariable UUID id) {
        return mapToResponse(orderService.getOrderById(id));
    }

    @Operation(summary = "Cancel an order", description = "Cancels a PENDING or CONFIRMED order and synchronously releases reserved inventory.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order cancelled successfully"),
            @ApiResponse(responseCode = "400", description = "Order cannot be cancelled in its current state"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    @PostMapping("/{id}/cancel")
    public OrderResponse cancelOrder(@PathVariable UUID id) {
        orderService.cancelOrder(id);
        return mapToResponse(orderService.getOrderById(id));
    }

    private OrderResponse mapToResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream().map(item ->
                OrderItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .unitPrice(item.getUnitPrice())
                        .quantity(item.getQuantity())
                        .build()
        ).collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .build();
    }
}