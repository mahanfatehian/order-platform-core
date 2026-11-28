package com.orderprocessing.webui.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutRateTableTest {
    @Test
    void publishedRatesRoundDownToTheRial() {
        assertThat(tax("100.00", 0)).isZero();
        assertThat(tax("100.00", 1)).isEqualTo(1);
        assertThat(tax("100.00", 100)).isEqualTo(100);
        assertThat(tax("100.00", 500)).isEqualTo(500);
        assertThat(tax("100.00", 1000)).isEqualTo(1000);
        assertThat(tax("100.00", 2500)).isEqualTo(2500);
        assertThat(tax("100.00", 10_000)).isEqualTo(10_000);
        assertThat(tax("29.90", 1000)).isEqualTo(299);
    }

    @Test
    void theChargedAmountIsWhatTheWalletMustCover() {
        CheckoutCharge charge = CheckoutCharge.quote(new BigDecimal("29.90"), 1000);
        assertThat(charge.goodsMinor()).isEqualTo(2990);
        assertThat(charge.chargedMinor()).isEqualTo(3289);
        assertThat(charge.canSpend(3288L)).isFalse();
        assertThat(charge.canSpend(3289L)).isTrue();
    }

    private static long tax(String goods, int rateBps) {
        return CheckoutCharge.quote(new BigDecimal(goods), rateBps).taxMinor();
    }
}
