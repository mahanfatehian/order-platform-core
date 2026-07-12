package com.orderprocessing.orderservice.controller;

import com.orderprocessing.orderservice.dto.CreateOrderRequest;
import com.orderprocessing.orderservice.dto.OrderResponse;
import com.orderprocessing.orderservice.dto.PageResponse;
import com.orderprocessing.orderservice.model.Order;
import com.orderprocessing.orderservice.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Validated
public class OrderController {
    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "updatedAt", "status", "totalAmount");
    private final OrderService orderService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<OrderResponse> getAllOrders(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Order.Status status,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return orderService.getAdminOrders(status, userId, orderId, search, pageRequest(page, size, sort));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<OrderResponse> getAdminOrders(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Order.Status status,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return orderService.getAdminOrders(status, userId, orderId, search, pageRequest(page, size, sort));
    }

    @GetMapping("/my-orders")
    public PageResponse<OrderResponse> getMyOrders(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Order.Status status,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return orderService.getMyOrders(userId(jwt), status, pageRequest(page, size, sort));
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        OrderResponse created = orderService.createOrder(userId(jwt), request, idempotencyKey,
                correlationId(correlationId));
        return ResponseEntity.created(URI.create("/api/orders/" + created.getId())).body(created);
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                                  Authentication authentication) {
        return orderService.getOrder(id, userId(jwt), isAdmin(authentication));
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancelOrder(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                                     Authentication authentication,
                                     @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return orderService.cancelOrder(id, userId(jwt), isAdmin(authentication), correlationId(correlationId));
    }

    @GetMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public OrderResponse getAdminOrder(@PathVariable UUID id) {
        return orderService.getAdminOrder(id);
    }

    @PostMapping("/admin/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public OrderResponse cancelAdminOrder(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                                          @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return orderService.cancelOrder(id, userId(jwt), true, correlationId(correlationId));
    }

    static PageRequest pageRequest(int page, int size, String sortValue) {
        String[] parts = sortValue == null ? new String[0] : sortValue.split(",", 2);
        String property = parts.length == 0 || !SORT_FIELDS.contains(parts[0]) ? "createdAt" : parts[0];
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(direction, property));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("userId"));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private String correlationId(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }
}
