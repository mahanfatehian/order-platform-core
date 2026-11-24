package com.orderprocessing.orderservice.carrier;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CarrierPortBoundaryTest {
    @Test
    void theFakeCarrierCannotSetOrderStatusOrWriteTheWallet() throws Exception {
        String source = Files.readString(root().resolve(
                "order-service/src/test/java/com/orderprocessing/orderservice/carrier/FakeCarrier.java"));
        assertThat(source).doesNotContain("setStatus");
        assertThat(source).doesNotContain("Order.Status");
        assertThat(source).doesNotContain("WalletLedger");
        assertThat(source).doesNotContain("placeHold");
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
