package com.orderprocessing.orderservice.tax;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TaxInvoiceBoundaryTest {
    @Test
    void theInvoiceDoesNotNameAnAuthorityOrEditAmounts() throws Exception {
        String entity = Files.readString(root().resolve(
                "order-service/src/main/java/com/orderprocessing/orderservice/tax/TaxInvoice.java"));
        String issuer = Files.readString(root().resolve(
                "order-service/src/main/java/com/orderprocessing/orderservice/tax/TaxInvoiceIssuer.java"));
        assertThat(entity.toLowerCase()).doesNotContain("vat number");
        assertThat(entity.toLowerCase()).doesNotContain("tax authority");
        assertThat(issuer).doesNotContain("revise");
        assertThat(issuer).doesNotContain("WalletLedger");
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
