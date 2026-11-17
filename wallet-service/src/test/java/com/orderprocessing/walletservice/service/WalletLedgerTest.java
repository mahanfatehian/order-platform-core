package com.orderprocessing.walletservice.service;

import com.orderprocessing.walletservice.exception.WalletException;
import com.orderprocessing.walletservice.model.HoldStatus;
import com.orderprocessing.walletservice.model.LedgerEntry;
import com.orderprocessing.walletservice.repository.HoldRepository;
import com.orderprocessing.walletservice.repository.LedgerEntryRepository;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.datasource.hikari.maximum-pool-size=6"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(WalletLedger.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WalletLedgerTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

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
    @Autowired
    private LedgerEntryRepository entries;
    @Autowired
    private HoldRepository holds;

    @Test
    void creditIncreasesAvailableBalance() {
        UUID userId = UUID.randomUUID();
        ledger.credit(userId, 1_000, "opening", "credit-1", "admin", "ADMIN", "corr");
        WalletLedger.Balance balance = ledger.balance(userId);
        assertThat(balance.available()).isEqualTo(1_000);
        assertThat(balance.booked()).isEqualTo(1_000);
        assertThat(balance.currency()).isEqualTo("IRR");
        assertThat(ledger.entries(userId)).extracting(entry -> entry.getReason()).containsExactly("opening");
    }

    @Test
    void holdFailsWhenAvailableIsShortAndWritesNothing() {
        UUID userId = UUID.randomUUID();
        ledger.credit(userId, 100, "opening", key(), "admin", "ADMIN", "corr");
        long before = entries.count();
        assertThatThrownBy(() -> ledger.placeHold(userId, UUID.randomUUID(), 101, key(), "corr"))
                .isInstanceOf(WalletException.class)
                .extracting("code")
                .isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(entries.count()).isEqualTo(before);
        assertThat(ledger.balance(userId).available()).isEqualTo(100);
    }

    @Test
    void holdForUnknownUserWritesNothing() {
        long before = entries.count();
        assertThatThrownBy(() -> ledger.placeHold(UUID.randomUUID(), UUID.randomUUID(), 1, key(), "corr"))
                .isInstanceOf(WalletException.class)
                .extracting("code")
                .isEqualTo("WALLET_NOT_FOUND");
        assertThat(entries.count()).isEqualTo(before);
    }

    @Test
    void twoConcurrentHoldsCannotSpendTheSameRial() throws Exception {
        UUID userId = UUID.randomUUID();
        ledger.credit(userId, 100, "opening", key(), "admin", "ADMIN", "corr");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicInteger wins = new AtomicInteger();
        AtomicInteger losses = new AtomicInteger();
        try {
            Future<?> first = pool.submit(() -> raceHold(userId, start, wins, losses));
            Future<?> second = pool.submit(() -> raceHold(userId, start, wins, losses));
            start.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(wins).hasValue(1);
        assertThat(losses).hasValue(1);
        assertThat(ledger.balance(userId).available()).isEqualTo(40);
        assertThat(ledger.balance(userId).available()).isNotNegative();
    }

    @Test
    void sameKeyAndSameBodyReturnsOneEntry() {
        UUID userId = UUID.randomUUID();
        String idempotencyKey = key();
        LedgerEntry first = ledger.credit(userId, 50, "opening", idempotencyKey, "admin", "ADMIN", "corr");
        LedgerEntry second = ledger.credit(userId, 50, "opening", idempotencyKey, "admin", "ADMIN", "corr");
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(ledger.balance(userId).available()).isEqualTo(50);
    }

    @Test
    void sameKeyAndDifferentAmountConflicts() {
        UUID userId = UUID.randomUUID();
        String idempotencyKey = key();
        ledger.credit(userId, 50, "opening", idempotencyKey, "admin", "ADMIN", "corr");
        assertThatThrownBy(() -> ledger.credit(userId, 51, "opening", idempotencyKey, "admin", "ADMIN", "corr"))
                .isInstanceOf(WalletException.class)
                .extracting("code")
                .isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(ledger.balance(userId).available()).isEqualTo(50);
    }

    @Test
    void captureOfMissingHoldFails() {
        assertThatThrownBy(() -> ledger.captureHold(UUID.randomUUID(), key(), "corr"))
                .isInstanceOf(WalletException.class)
                .extracting("code")
                .isEqualTo("HOLD_NOT_FOUND");
    }

    @Test
    void releaseOfOpenHoldRestoresAvailableBalance() {
        UUID userId = UUID.randomUUID();
        ledger.credit(userId, 80, "opening", key(), "admin", "ADMIN", "corr");
        LedgerEntry hold = ledger.placeHold(userId, UUID.randomUUID(), 30, key(), "corr");
        assertThat(ledger.balance(userId).available()).isEqualTo(50);
        ledger.releaseHold(hold.getHoldId(), key(), "corr");
        assertThat(ledger.balance(userId).available()).isEqualTo(80);
        assertThat(holds.findById(hold.getHoldId()).orElseThrow().getStatus()).isEqualTo(HoldStatus.RELEASED);
    }

    @Test
    void captureAfterReleaseFails() {
        UUID userId = UUID.randomUUID();
        ledger.credit(userId, 80, "opening", key(), "admin", "ADMIN", "corr");
        LedgerEntry hold = ledger.placeHold(userId, UUID.randomUUID(), 30, key(), "corr");
        ledger.releaseHold(hold.getHoldId(), key(), "corr");
        assertThatThrownBy(() -> ledger.captureHold(hold.getHoldId(), key(), "corr"))
                .isInstanceOf(WalletException.class)
                .extracting("code")
                .isEqualTo("HOLD_NOT_OPEN");
        assertThat(ledger.balance(userId).booked()).isEqualTo(80);
    }

    @Test
    void releaseAfterCaptureFails() {
        UUID userId = UUID.randomUUID();
        ledger.credit(userId, 80, "opening", key(), "admin", "ADMIN", "corr");
        LedgerEntry hold = ledger.placeHold(userId, UUID.randomUUID(), 30, key(), "corr");
        ledger.captureHold(hold.getHoldId(), key(), "corr");
        assertThatThrownBy(() -> ledger.releaseHold(hold.getHoldId(), key(), "corr"))
                .isInstanceOf(WalletException.class)
                .extracting("code")
                .isEqualTo("HOLD_NOT_OPEN");
        assertThat(ledger.balance(userId).available()).isEqualTo(50);
        assertThat(ledger.balance(userId).booked()).isEqualTo(50);
    }

    @Test
    void secondCaptureReturnsTheFirstEntry() {
        UUID userId = UUID.randomUUID();
        ledger.credit(userId, 80, "opening", key(), "admin", "ADMIN", "corr");
        LedgerEntry hold = ledger.placeHold(userId, UUID.randomUUID(), 30, key(), "corr");
        LedgerEntry first = ledger.captureHold(hold.getHoldId(), key(), "corr");
        LedgerEntry second = ledger.captureHold(hold.getHoldId(), key(), "corr");
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(ledger.balance(userId).booked()).isEqualTo(50);
    }

    @Test
    void refundWithoutCaptureFails() {
        UUID userId = UUID.randomUUID();
        LedgerEntry credit = ledger.credit(userId, 80, "opening", key(), "admin", "ADMIN", "corr");
        assertThatThrownBy(() -> ledger.refundCapture(credit.getId(), key(), "corr"))
                .isInstanceOf(WalletException.class)
                .extracting("code")
                .isEqualTo("NOT_A_CAPTURE");
    }

    @Test
    void zeroAmountIsRejected() {
        assertThatThrownBy(() -> ledger.credit(UUID.randomUUID(), 0, "opening", key(), "admin", "ADMIN", "corr"))
                .isInstanceOf(WalletException.class)
                .extracting("code")
                .isEqualTo("AMOUNT_NOT_POSITIVE");
    }

    private void raceHold(UUID userId, CountDownLatch start, AtomicInteger wins, AtomicInteger losses) {
        try {
            start.await();
            ledger.placeHold(userId, UUID.randomUUID(), 60, key(), "corr");
            wins.incrementAndGet();
        } catch (WalletException exception) {
            losses.incrementAndGet();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }
}
