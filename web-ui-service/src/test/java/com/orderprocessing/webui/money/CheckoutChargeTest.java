package com.orderprocessing.webui.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckoutChargeTest {
    @Test
    void aZeroRateChargesTheGoodsOnly() {
        CheckoutCharge charge = CheckoutCharge.quote(new BigDecimal("10.00"), 0);
        assertThat(charge.goodsMinor()).isEqualTo(1000);
        assertThat(charge.taxMinor()).isZero();
        assertThat(charge.chargedMinor()).isEqualTo(1000);
        assertThat(charge.canSpend(1000L)).isTrue();
        assertThat(charge.canSpend(999L)).isFalse();
    }

    @Test
    void tenPercentOfTenRialsIsOneRial() {
        CheckoutCharge charge = CheckoutCharge.quote(new BigDecimal("10.00"), 1000);
        assertThat(charge.taxMinor()).isEqualTo(100);
        assertThat(charge.chargedMinor()).isEqualTo(1100);
        assertThat(charge.lines()).contains("Goods: 1000 rials", "Tax: 100 rials", "Charged: 1100 rials");
    }

    @Test
    void aFractionOfARialIsNotRoundedUp() {
        assertThat(CheckoutCharge.taxOf(1000, 1)).isZero();
    }

    @Test
    void theCustomerMustCoverTheChargedAmountNotOnlyTheGoods() {
        CheckoutCharge charge = CheckoutCharge.quote(new BigDecimal("10.00"), 1000);
        assertThat(charge.canSpend(1000L)).isFalse();
        assertThat(charge.canSpend(1100L)).isTrue();
        assertThat(charge.refusal(1000L)).isEqualTo("This balance cannot cover the goods and the tax");
        assertThat(charge.refusal(null)).isEqualTo("This account has no wallet yet");
        assertThat(charge.refusal(1100L)).isNull();
    }

    @Test
    void aRateAboveOneHundredPercentIsRejected() {
        assertThatThrownBy(() -> CheckoutCharge.quote(new BigDecimal("10.00"), 10_001))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aScaleThatDoesNotMatchTheWalletIsRejected() {
        assertThatThrownBy(() -> CheckoutCharge.quote(new BigDecimal("10.001"), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theLinesDoNotMultiplyRialsByOneHundred() {
        String text = String.join("\n", CheckoutCharge.quote(new BigDecimal("29.90"), 0).lines());
        assertThat(text).contains("Goods: 2990 rials");
        assertThat(text).doesNotContain("299000");
    }
}
