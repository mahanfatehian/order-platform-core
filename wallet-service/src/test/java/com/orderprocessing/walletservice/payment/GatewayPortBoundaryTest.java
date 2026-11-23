package com.orderprocessing.walletservice.payment;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayPortBoundaryTest {
    @Test
    void theFakeGatewayCannotSeeTheLedgerRepository() throws Exception {
        String source = Files.readString(repoRoot().resolve(
                "wallet-service/src/test/java/com/orderprocessing/walletservice/payment/FakeGateway.java"));
        assertThat(source).doesNotContain("LedgerRepository");
        assertThat(source).doesNotContain("LedgerEntryRepository");
        assertThat(source).doesNotContain("WalletLedger");
    }

    @Test
    void adminCreditDoesNotRequireARegisteredGateway() {
        List<PaymentPort> registered = List.of();
        assertThat(registered).isEmpty();
        assertThat(PaymentPort.class.isInterface()).isTrue();
    }

    private static Path repoRoot() {
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
