package com.orderprocessing.walletservice.service;

import com.orderprocessing.walletservice.model.EntryType;
import com.orderprocessing.walletservice.model.LedgerEntry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.datasource.hikari.maximum-pool-size=6"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(WalletLedger.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WalletPurchaseAcceptanceTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final long CREDIT = 10_000L;
    private static final long HOLD_AMOUNT = 2_500L;

    @DynamicPropertySource
    static void postgresql(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterAll
    static void stopPostgres() {
        POSTGRES.stop();
    }

    @Autowired
    private WalletLedger ledger;

    @Test
    void purchaseCapturesOnceAndAReplayOrNewKeyDoesNotCaptureAgain() {
        UUID userId = UUID.randomUUID();
        ledger.credit(userId, CREDIT, "admin credit", key(), "admin", "ADMIN", "corr");

        LedgerEntry hold = ledger.placeHold(userId, UUID.randomUUID(), HOLD_AMOUNT, key(), "corr");
        String captureKey = key();
        LedgerEntry captured = ledger.captureHold(hold.getHoldId(), captureKey, "corr");

        assertThat(ledger.balance(userId).booked()).isEqualTo(CREDIT - HOLD_AMOUNT);
        assertThat(captured.getEntryType()).isEqualTo(EntryType.CAPTURE);
        assertThat(captureCount(userId)).isEqualTo(1);

        LedgerEntry replay = ledger.captureHold(hold.getHoldId(), captureKey, "corr");
        assertThat(replay.getId()).isEqualTo(captured.getId());
        assertThat(ledger.balance(userId).booked()).isEqualTo(CREDIT - HOLD_AMOUNT);
        assertThat(captureCount(userId)).isEqualTo(1);

        LedgerEntry second = ledger.captureHold(hold.getHoldId(), key(), "corr");
        assertThat(second.getId()).isEqualTo(captured.getId());
        assertThat(ledger.balance(userId).booked()).isEqualTo(CREDIT - HOLD_AMOUNT);
        assertThat(captureCount(userId)).isEqualTo(1);
    }

    private long captureCount(UUID userId) {
        return ledger.entries(userId).stream().filter(entry -> entry.getEntryType() == EntryType.CAPTURE).count();
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }
}
