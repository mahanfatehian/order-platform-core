package com.orderprocessing.webui.shop;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperatorCopyTest {
    @Test
    void aBlankTitleStaysShop() {
        assertThat(new OperatorCopy("  ", null).title()).isEqualTo("Shop");
    }

    @Test
    void aRankingClaimIsRejected() {
        assertThatThrownBy(() -> new OperatorCopy("Number one shop", "Lamps"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OperatorCopy("Shop", "Our ranking is high"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
