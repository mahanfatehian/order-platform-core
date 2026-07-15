package com.orderprocessing.webui.service;

import com.orderprocessing.webui.dto.CartView;
import com.orderprocessing.webui.dto.QuoteItemView;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Validates that a store quote is an exact, currently orderable projection of
 * the immutable cart snapshot that will be sent to the order service.
 */
@Component
public class CartQuoteValidator {

    public CartView validate(Map<UUID, Integer> requested, List<QuoteItemView> quotedItems) {
        Map<UUID, Integer> expected = requested == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(requested));
        List<QuoteItemView> quoted = quotedItems == null ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(quotedItems));
        Set<UUID> seenProductIds = new HashSet<>();

        boolean exactAndAvailable = !expected.isEmpty()
                && expected.values().stream().allMatch(quantity -> quantity != null && quantity > 0)
                && quoted.size() == expected.size()
                && quoted.stream().allMatch(item -> matches(item, expected, seenProductIds))
                && seenProductIds.equals(expected.keySet());

        BigDecimal total = quoted.stream()
                .filter(item -> item != null && item.unitPrice() != null && item.requestedQuantity() > 0)
                .map(QuoteItemView::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartView(quoted, total, exactAndAvailable);
    }

    private boolean matches(QuoteItemView item, Map<UUID, Integer> expected, Set<UUID> seenProductIds) {
        if (item == null || item.productId() == null || !seenProductIds.add(item.productId())) {
            return false;
        }
        Integer requestedQuantity = expected.get(item.productId());
        return requestedQuantity != null
                && requestedQuantity > 0
                && item.requestedQuantity() == requestedQuantity
                && item.unitPrice() != null
                && item.unitPrice().signum() >= 0
                && item.active()
                && item.available()
                && item.availableQuantity() >= requestedQuantity;
    }
}
