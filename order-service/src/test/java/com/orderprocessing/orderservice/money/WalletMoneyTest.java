package com.orderprocessing.orderservice.money;

import com.orderprocessing.orderservice.exception.QuoteScaleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletMoneyTest {
    @Test
    void convertsATwoScaleQuoteWithoutRounding() {
        assertThat(WalletMoney.minorUnits(new BigDecimal("29.90"))).isEqualTo(2990);
        assertThat(WalletMoney.minorUnits(new BigDecimal("30"))).isEqualTo(3000);
    }

    @Test
    void aThirdDecimalIsRejected() {
        assertThatThrownBy(() -> WalletMoney.minorUnits(new BigDecimal("29.901")))
                .isInstanceOf(QuoteScaleException.class);
    }
}
