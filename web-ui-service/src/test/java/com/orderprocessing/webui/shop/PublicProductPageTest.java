package com.orderprocessing.webui.shop;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PublicProductPageTest {
    @Test
    void theRenderedPageKeepsTheCatalogPrice() {
        String page = new PublicProductPage().render(new PublicProduct(UUID.randomUUID(), "Lamp", "10.00", true));
        assertThat(page).contains("10.00");
        assertThat(page).doesNotContain("1000");
    }
}
