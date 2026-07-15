package com.orderprocessing.webui.service;

import com.orderprocessing.webui.dto.CartView;
import com.orderprocessing.webui.dto.QuoteItemView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CartQuoteValidatorTest {
    private static final UUID PRODUCT_ID = UUID.fromString("aaaaaaaa-2222-2222-2222-222222222222");
    private final CartQuoteValidator validator = new CartQuoteValidator();

    @Test
    void reducedQuantityBecomesCheckoutReadyWhenFreshInventoryCanCoverIt() {
        CartView stale = validator.validate(Map.of(PRODUCT_ID, 8),
                List.of(item(8, 3, false)));
        CartView refreshed = validator.validate(Map.of(PRODUCT_ID, 2),
                List.of(item(2, 3, true)));

        assertThat(stale.checkoutReady()).isFalse();
        assertThat(refreshed.checkoutReady()).isTrue();
        assertThat(refreshed.total()).isEqualByComparingTo("39.98");
    }

    @Test
    void rejectsIncompleteDuplicateOrQuantityMismatchedQuotes() {
        UUID secondProduct = UUID.fromString("bbbbbbbb-2222-2222-2222-222222222222");
        Map<UUID, Integer> requested = Map.of(PRODUCT_ID, 2, secondProduct, 1);

        assertThat(validator.validate(requested, List.of(item(2, 3, true))).checkoutReady()).isFalse();
        assertThat(validator.validate(requested, List.of(item(2, 3, true), item(2, 3, true)))
                .checkoutReady()).isFalse();
        assertThat(validator.validate(Map.of(PRODUCT_ID, 2), List.of(item(1, 3, true)))
                .checkoutReady()).isFalse();
    }

    @Test
    void requiresAnActiveAvailableProductAndSufficientLiveQuantity() {
        QuoteItemView inactive = new QuoteItemView(PRODUCT_ID, "Desk Lamp", new BigDecimal("19.99"),
                false, 2, 3, true);
        QuoteItemView availabilityFlagFalse = item(2, 3, false);
        QuoteItemView insufficient = item(2, 1, true);

        assertThat(validator.validate(Map.of(PRODUCT_ID, 2), List.of(inactive)).checkoutReady()).isFalse();
        assertThat(validator.validate(Map.of(PRODUCT_ID, 2), List.of(availabilityFlagFalse)).checkoutReady()).isFalse();
        assertThat(validator.validate(Map.of(PRODUCT_ID, 2), List.of(insufficient)).checkoutReady()).isFalse();
    }

    private QuoteItemView item(int requested, int available, boolean availability) {
        return new QuoteItemView(PRODUCT_ID, "Desk Lamp", new BigDecimal("19.99"), true,
                requested, available, availability);
    }
}
