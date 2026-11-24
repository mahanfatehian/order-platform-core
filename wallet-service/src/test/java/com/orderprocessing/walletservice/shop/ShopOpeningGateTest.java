package com.orderprocessing.walletservice.shop;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ShopOpeningGateTest {
    @Test
    void defaultComposeHasNoPaymentGateway() throws Exception {
        String compose = Files.readString(root().resolve("docker-compose.yml"));
        assertThat(compose.toLowerCase()).doesNotContain("zarinpal");
        assertThat(compose.toLowerCase()).doesNotContain("stripe");
        assertThat(compose).doesNotContain("payment-gateway:");
    }

    private static Path root() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("docker-compose.yml"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("repo root not found");
    }
}
