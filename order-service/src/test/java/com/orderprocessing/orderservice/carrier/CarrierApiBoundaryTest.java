package com.orderprocessing.orderservice.carrier;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CarrierApiBoundaryTest {
    @Test
    void theHandoffHasNoRateAndNoPrice() throws Exception {
        String entity = Files.readString(root().resolve(
                "order-service/src/main/java/com/orderprocessing/orderservice/carrier/CarrierHandoff.java"));
        String view = Files.readString(root().resolve(
                "order-service/src/main/java/com/orderprocessing/orderservice/carrier/CarrierHandoffView.java"));
        assertThat(entity.toLowerCase()).doesNotContain("rate");
        assertThat(entity.toLowerCase()).doesNotContain("price");
        assertThat(view.toLowerCase()).doesNotContain("price");
        assertThat(entity).doesNotContain("setStatus");
    }

    private static Path root() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("order-service"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("repo root not found");
    }
}
