package com.orderprocessing.webui.shop;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InvoicePageTest {
    @Test
    void theOrderPageShowsTheSnapshotAndDoesNotNameAnAuthority() throws Exception {
        String page = Files.readString(root().resolve(
                "web-ui-service/src/main/resources/templates/orders/detail.html"));
        assertThat(page).contains("invoice-snapshot");
        assertThat(page).contains("does not name a tax authority");
        assertThat(page.toLowerCase()).doesNotContain("vat");
    }

    private static Path root() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("web-ui-service"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("repo root not found");
    }
}
