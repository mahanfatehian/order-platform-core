package com.orderprocessing.webui.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderView(
        UUID id,
        UUID userId,
        String status,
        BigDecimal totalAmount,
        String failureReason,
        List<OrderItemView> items,
        Instant createdAt,
        Instant updatedAt,
        boolean cancellable,
        String trackingReference
) {
    public OrderView {
        items = items == null ? List.of() : List.copyOf(items);
    }
    public OrderView(UUID id, UUID userId, String status, BigDecimal totalAmount, String failureReason,
                     List<OrderItemView> items, Instant createdAt, Instant updatedAt, boolean cancellable) {
        this(id, userId, status, totalAmount, failureReason, items, createdAt, updatedAt, cancellable, null);
    }
    public boolean pending() { return "PENDING".equals(status); }
    public boolean confirmed() { return "CONFIRMED".equals(status); }
    public boolean packaged() { return "PACKAGED".equals(status); }
    public boolean shipped() { return "SHIPPED".equals(status); }
    public boolean delivered() { return "DELIVERED".equals(status); }
    public boolean failed() { return "FAILED".equals(status); }
    public boolean cancelled() { return "CANCELLED".equals(status); }
    public boolean inProgress() {
        return pending() || confirmed() || packaged() || shipped();
    }
    public boolean terminal() { return delivered() || failed() || cancelled(); }
    public int lifecycleStage() {
        return switch (status == null ? "" : status) {
            case "PENDING" -> 1;
            case "CONFIRMED" -> 2;
            case "PACKAGED" -> 3;
            case "SHIPPED" -> 4;
            case "DELIVERED" -> 5;
            default -> 0;
        };
    }
    public boolean reachedStage(int stage) {
        return stage >= 1 && stage <= 5 && lifecycleStage() >= stage;
    }
    public int itemCount() { return items.stream().mapToInt(OrderItemView::quantity).sum(); }
}
