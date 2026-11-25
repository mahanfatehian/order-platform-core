package com.orderprocessing.webui.shop;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InactivePublicProductTest {
    @Test
    void anInactiveProductCannotBeRendered() {
        assertThatThrownBy(() -> new PublicCatalog().page(
                new PublicProduct(UUID.randomUUID(), "Hidden", "10.00", false)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
