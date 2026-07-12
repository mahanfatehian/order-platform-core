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
        boolean cancellable
) {
    public OrderView {
        items = items == null ? List.of() : List.copyOf(items);
    }
    public boolean pending() { return "PENDING".equals(status); }
    public boolean terminal() { return !pending(); }
    public int itemCount() { return items.stream().mapToInt(OrderItemView::quantity).sum(); }
}
