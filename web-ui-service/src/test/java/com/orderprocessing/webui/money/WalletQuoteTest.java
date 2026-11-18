package com.orderprocessing.webui.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class WalletQuoteTest {
    @Test
    void aHoldReducesAvailableWithoutChangingTheQuotedTotal() {
        assertThat(WalletQuote.minorUnits(new BigDecimal("20.00"))).isEqualTo(2000);
        assertThat(WalletQuote.canSpend(new BigDecimal("20.00"), 3000L)).isTrue();
        assertThat(WalletQuote.canSpend(new BigDecimal("20.00"), 1999L)).isFalse();
    }
}
