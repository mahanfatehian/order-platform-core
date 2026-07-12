package com.orderprocessing.webui.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartView(List<QuoteItemView> items, BigDecimal total, boolean checkoutReady) {
    public CartView { items = items == null ? List.of() : List.copyOf(items); }
    public static CartView empty() { return new CartView(List.of(), BigDecimal.ZERO, false); }
    public boolean hasAvailabilityWarnings() { return items.stream().anyMatch(item -> !item.available()); }
}
