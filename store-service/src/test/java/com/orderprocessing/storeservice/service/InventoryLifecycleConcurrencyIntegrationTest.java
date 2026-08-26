package com.orderprocessing.storeservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.kafkacommon.event.OrderCancelledEvent;
import com.orderprocessing.kafkacommon.event.OrderPlacedEvent;
import com.orderprocessing.storeservice.model.Inventory;
import com.orderprocessing.storeservice.model.InventoryOrderLifecycle;
import com.orderprocessing.storeservice.model.InventoryReservation;
import com.orderprocessing.storeservice.model.Product;
import com.orderprocessing.storeservice.repository.InventoryOrderLifecycleRepository;
import com.orderprocessing.storeservice.repository.InventoryRepository;
import com.orderprocessing.storeservice.repository.InventoryReservationRepository;
import com.orderprocessing.storeservice.repository.ProcessedKafkaEventRepository;
import com.orderprocessing.storeservice.repository.ProductRepository;
import com.orderprocessing.storeservice.repository.StoreOutboxEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@EnabledIf(value = "com.orderprocessing.storeservice.support.PostgresAvailability#present",
        disabledReason = "Needs Docker for Testcontainers, or -Dtask5.postgres.url pointing at a database")
@DataJpaTest(properties = {
        "spring.jpa.show-sql=false",
        "spring.datasource.hikari.maximum-pool-size=6"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(InventoryLifecycleConcurrencyIntegrationTest.ServiceConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InventoryLifecycleConcurrencyIntegrationTest {

    private static final String EXTERNAL_POSTGRES_URL = System.getProperty("task5.postgres.url");
    private static final PostgreSQLContainer<?> POSTGRES = EXTERNAL_POSTGRES_URL == null
            ? new PostgreSQLContainer<>("postgres:16-alpine") : null;

    @DynamicPropertySource
    static void postgresqlProperties(DynamicPropertyRegistry registry) {
        if (EXTERNAL_POSTGRES_URL != null) {
            registry.add("spring.datasource.url", () -> EXTERNAL_POSTGRES_URL);
            registry.add("spring.datasource.username", () -> System.getProperty("task5.postgres.username"));
            registry.add("spring.datasource.password", () -> System.getProperty("task5.postgres.password"));
            return;
        }
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterAll
    static void stopPostgres() {
        if (POSTGRES != null) {
            POSTGRES.stop();
        }
    }

    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private InventoryReservationRepository reservationRepository;
    @Autowired
    private InventoryOrderLifecycleRepository lifecycleRepository;

    private ExecutorService executor;

    @AfterEach
    void shutdownExecutor() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void placementThenCompensationSerializesAndReleasesExactlyOnce() throws Exception {
        UUID productId = seedProductWithInventory(10);
        UUID orderId = UUID.randomUUID();

        runContendingTransactions(
                () -> inventoryService.processOrderPlaced(placed(orderId, productId, 3), "orders", 0, 1),
                () -> inventoryService.processOrderCancelled(cancelled(orderId), "orders", 0, 2));

        TransactionSnapshot snapshot = snapshot(orderId, productId);
        assertThat(snapshot.lifecycleState()).isEqualTo(InventoryOrderLifecycle.State.RELEASED);
        assertThat(snapshot.reservations()).singleElement().satisfies(reservation -> {
            assertThat(reservation.getStatus()).isEqualTo(InventoryReservation.Status.RELEASED);
            assertThat(reservation.getQuantity()).isEqualTo(3);
        });
        assertThat(snapshot.inventory().getQuantity()).isEqualTo(10);
        assertThat(snapshot.inventory().getReservedQuantity()).isZero();
    }

    @Test
    void compensationThenPlacementLeavesReleasedTombstoneAndNeverReserves() throws Exception {
        UUID productId = seedProductWithInventory(10);
        UUID orderId = UUID.randomUUID();

        runContendingTransactions(
                () -> inventoryService.processOrderCancelled(cancelled(orderId), "orders", 0, 3),
                () -> inventoryService.processOrderPlaced(placed(orderId, productId, 3), "orders", 0, 4));

        TransactionSnapshot snapshot = snapshot(orderId, productId);
        assertThat(snapshot.lifecycleState()).isEqualTo(InventoryOrderLifecycle.State.RELEASED);
        assertThat(snapshot.reservations()).isEmpty();
        assertThat(snapshot.inventory().getQuantity()).isEqualTo(10);
        assertThat(snapshot.inventory().getReservedQuantity()).isZero();
    }

    private void runContendingTransactions(Runnable first, Runnable second) throws Exception {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        CountDownLatch firstHasGuard = new CountDownLatch(1);
        CountDownLatch secondAttemptsGuard = new CountDownLatch(1);
        CountDownLatch commitFirst = new CountDownLatch(1);
        executor = Executors.newFixedThreadPool(2);

        Future<?> firstTransaction = executor.submit(() -> transactions.executeWithoutResult(status -> {
            first.run();
            firstHasGuard.countDown();
            await(commitFirst);
        }));
        assertThat(firstHasGuard.await(10, TimeUnit.SECONDS)).isTrue();

        Future<?> secondTransaction = executor.submit(() -> transactions.executeWithoutResult(status -> {
            secondAttemptsGuard.countDown();
            second.run();
        }));
        assertThat(secondAttemptsGuard.await(10, TimeUnit.SECONDS)).isTrue();
        assertBlockedOnFirstTransaction(secondTransaction);

        commitFirst.countDown();
        firstTransaction.get(10, TimeUnit.SECONDS);
        secondTransaction.get(10, TimeUnit.SECONDS);
    }

    private void assertBlockedOnFirstTransaction(Future<?> transaction) throws Exception {
        try {
            transaction.get(250, TimeUnit.MILLISECONDS);
            fail("The second transaction completed before the first lifecycle lock was released");
        } catch (TimeoutException expected) {
            // The first transaction keeps the locked lifecycle row uncommitted.
        }
    }

    private UUID seedProductWithInventory(int quantity) {
        UUID productId = UUID.randomUUID();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant now = Instant.now();
            Product product = new Product();
            product.setId(productId);
            product.setName("Lifecycle test product");
            product.setSku("lifecycle-" + productId);
            product.setPrice(BigDecimal.ONE);
            product.setCategory(Product.Category.OTHER);
            product.setActive(true);
            product.setCreatedAt(now);
            product.setUpdatedAt(now);
            productRepository.save(product);

            Inventory inventory = new Inventory();
            inventory.setProductId(productId);
            inventory.setQuantity(quantity);
            inventory.setReservedQuantity(0);
            inventory.setLastUpdated(now);
            inventoryRepository.save(inventory);
        });
        return productId;
    }

    private TransactionSnapshot snapshot(UUID orderId, UUID productId) {
        return new TransactionTemplate(transactionManager).execute(status -> new TransactionSnapshot(
                lifecycleRepository.findById(orderId).orElseThrow().getState(),
                reservationRepository.findByOrderIdForUpdate(orderId),
                inventoryRepository.findById(productId).orElseThrow()));
    }

    private OrderPlacedEvent placed(UUID orderId, UUID productId, int quantity) {
        OrderPlacedEvent event = new OrderPlacedEvent();
        event.setEventId(UUID.randomUUID());
        event.setOrderId(orderId);
        event.setItems(Map.of(productId, quantity));
        event.setCorrelationId("integration-test");
        return event;
    }

    private OrderCancelledEvent cancelled(UUID orderId) {
        OrderCancelledEvent event = new OrderCancelledEvent();
        event.setEventId(UUID.randomUUID());
        event.setOrderId(orderId);
        event.setCorrelationId("integration-test");
        return event;
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for the concurrent transaction");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for the concurrent transaction", ex);
        }
    }

    private record TransactionSnapshot(InventoryOrderLifecycle.State lifecycleState,
                                       List<InventoryReservation> reservations,
                                       Inventory inventory) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ServiceConfiguration {
        @Bean
        InventoryService inventoryService(InventoryRepository inventoryRepository,
                                          InventoryOrderLifecycleRepository lifecycleRepository,
                                          InventoryReservationRepository reservationRepository,
                                          ProductRepository productRepository,
                                          ProcessedKafkaEventRepository processedEventRepository,
                                          StoreOutboxEventRepository outboxEventRepository) {
            return new InventoryService(inventoryRepository, lifecycleRepository, reservationRepository,
                    productRepository, processedEventRepository, outboxEventRepository,
                    new ObjectMapper().findAndRegisterModules());
        }
    }
}
