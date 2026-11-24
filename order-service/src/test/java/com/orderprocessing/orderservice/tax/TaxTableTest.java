package com.orderprocessing.orderservice.tax;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaxTableTest {
    @Test
    void aZeroRateAddsNothing() {
        assertThat(TaxTable.none().taxMinor(1000)).isZero();
    }

    @Test
    void tenPercentOfOneThousandRialsIsOneHundred() {
        assertThat(new TaxTable(1000).taxMinor(1000)).isEqualTo(100);
    }

    @Test
    void aFractionOfARialIsNotRoundedUp() {
        assertThat(new TaxTable(1).taxMinor(1000)).isZero();
    }

    @Test
    void aRateAboveOneHundredPercentIsRejected() {
        assertThatThrownBy(() -> new TaxTable(10_001))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
