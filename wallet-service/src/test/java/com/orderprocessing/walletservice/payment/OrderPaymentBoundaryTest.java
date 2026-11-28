package com.orderprocessing.walletservice.payment;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OrderPaymentBoundaryTest {
    @Test
    void theCaptureDoesNotSetOrderStatusOrCreditTheWallet() throws Exception {
        String source = Files.readString(root().resolve(
                "wallet-service/src/main/java/com/orderprocessing/walletservice/payment/OrderPaymentCapture.java"));
        assertThat(source).doesNotContain("setStatus");
        assertThat(source).doesNotContain("Order.Status");
        assertThat(source).doesNotContain(".credit(");
        assertThat(source).contains("captureHold");
    }

    private static Path root() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("wallet-service"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("repo root not found");
    }
}
