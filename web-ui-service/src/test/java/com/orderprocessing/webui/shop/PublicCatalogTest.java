package com.orderprocessing.webui.shop;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicCatalogTest {
    private final PublicCatalog catalog = new PublicCatalog();

    @Test
    void anInactiveProductIsNotPublished() {
        PublicProduct hidden = new PublicProduct(UUID.randomUUID(), "Hidden", "10.00", false);
        PublicProduct shown = new PublicProduct(UUID.randomUUID(), "Lamp", "10.00", true);
        assertThat(catalog.published(List.of(hidden, shown))).containsExactly(shown);
    }

    @Test
    void aPublicPageDoesNotInventADomainAGuestCheckoutOrAWallet() {
        String page = catalog.page(new PublicProduct(UUID.randomUUID(), "Lamp", "10.00", true));
        assertThat(page).contains("Lamp").contains("10.00").contains("operator has not set a public domain");
        assertThat(page.toLowerCase()).doesNotContain("guest");
        assertThat(page.toLowerCase()).doesNotContain("wallet");
        assertThat(page.toLowerCase()).doesNotContain("ranking");
    }

    @Test
    void robotsLeavesIndexingToTheOperator() {
        String robots = catalog.robots();
        assertThat(robots).contains("Disallow: /");
        assertThat(robots).contains("operator fills this file");
        assertThat(robots).doesNotContain("Sitemap:");
        assertThat(robots).doesNotContain("http");
    }
}
