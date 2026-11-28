package com.orderprocessing.webui.money;

import com.orderprocessing.webui.dto.WalletBalanceView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutReviewTest {
    @Test
    void aShortBalanceDisablesPlaceWhenTaxPushesTheChargeOverTheBalance() {
        CheckoutCharge charge = CheckoutCharge.quote(new BigDecimal("10.00"), 1000);
        CheckoutReview review = new CheckoutReview(charge, new WalletBalanceView(null, "IRR", 1000, 1000, 0), true);
        assertThat(review.shortBalance()).isTrue();
        assertThat(review.canPlace()).isFalse();
        assertThat(review.notice()).contains("goods and the tax");
    }

    @Test
    void aCoveredChargeCanBePlaced() {
        CheckoutCharge charge = CheckoutCharge.quote(new BigDecimal("10.00"), 0);
        CheckoutReview review = new CheckoutReview(charge, new WalletBalanceView(null, "IRR", 1000, 1000, 0), true);
        assertThat(review.canPlace()).isTrue();
        assertThat(review.notice()).contains("goods plus tax");
    }
}
