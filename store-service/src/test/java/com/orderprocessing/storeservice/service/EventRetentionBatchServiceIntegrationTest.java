package com.orderprocessing.storeservice.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIf(value = "com.orderprocessing.storeservice.support.PostgresAvailability#present",
        disabledReason = "Needs Docker for Testcontainers, or -Dtask5.postgres.url")
@DataJpaTest(properties = {
        "spring.jpa.show-sql=false",
        "spring.datasource.hikari.maximum-pool-size=3"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(EventRetentionBatchService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EventRetentionBatchServiceIntegrationTest {
    private static final String EXTERNAL_POSTGRES_URL = System.getProperty("task5.postgres.url");
    private static final PostgreSQLContainer<?> POSTGRES = EXTERNAL_POSTGRES_URL == null
            ? new PostgreSQLContainer<>("postgres:16-alpine") : null;
    private static final Instant CUTOFF = Instant.parse("2026-08-01T00:00:00Z");

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
    private EventRetentionBatchService batchService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private DataSource dataSource;

    private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        jdbc.getJdbcTemplate().update("delete from processed_kafka_events");
        jdbc.getJdbcTemplate().update("delete from store_outbox_events");
    }

    @Test
    void deletesOnlyEligibleRowsInExactBatches() {
        UUID unpublished = seedOutbox(false, false, null);
        UUID deadLettered = seedOutbox(false, true, null);
        UUID publishedDeadLettered = seedOutbox(true, true, CUTOFF.minus(4, ChronoUnit.DAYS));
        UUID boundary = seedOutbox(true, false, CUTOFF);
        seedOutbox(true, false, CUTOFF.minus(3, ChronoUnit.DAYS));
        seedOutbox(true, false, CUTOFF.minus(2, ChronoUnit.DAYS));
        seedOutbox(true, false, CUTOFF.minus(1, ChronoUnit.DAYS));
        UUID inboxBoundary = seedInbox(CUTOFF, 4);
        seedInbox(CUTOFF.minus(3, ChronoUnit.DAYS), 1);
        seedInbox(CUTOFF.minus(2, ChronoUnit.DAYS), 2);
        seedInbox(CUTOFF.minus(1, ChronoUnit.DAYS), 3);

        assertThat(batchService.deleteOutboxBatch(CUTOFF, 2)).isEqualTo(2);
        assertThat(batchService.deleteOutboxBatch(CUTOFF, 2)).isEqualTo(1);
        assertThat(batchService.deleteOutboxBatch(CUTOFF, 2)).isZero();
        assertThat(batchService.deleteInboxBatch(CUTOFF, 2)).isEqualTo(2);
        assertThat(batchService.deleteInboxBatch(CUTOFF, 2)).isEqualTo(1);
        assertThat(batchService.deleteInboxBatch(CUTOFF, 2)).isZero();

        assertThat(outboxExists(unpublished)).isTrue();
        assertThat(outboxExists(deadLettered)).isTrue();
        assertThat(outboxExists(publishedDeadLettered)).isTrue();
        assertThat(outboxExists(boundary)).isTrue();
        assertThat(inboxExists(inboxBoundary)).isTrue();
        assertThat(count("store_outbox_events")).isEqualTo(4);
        assertThat(count("processed_kafka_events")).isEqualTo(1);
    }

    @Test
    void commitsBatchBeforeOuterTransactionRollsBack() {
        seedOutbox(true, false, CUTOFF.minus(1, ChronoUnit.DAYS));
        TransactionTemplate outer = new TransactionTemplate(transactionManager);

        outer.executeWithoutResult(status -> {
            assertThat(batchService.deleteOutboxBatch(CUTOFF, 1)).isEqualTo(1);
            assertThat(eligibleOutboxCountFromSeparateConnection()).isZero();
            status.setRollbackOnly();
        });

        assertThat(eligibleOutboxCountFromSeparateConnection()).isZero();
    }

    private UUID seedOutbox(boolean published, boolean deadLettered, Instant publishedAt) {
        UUID id = UUID.randomUUID();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("aggregateId", UUID.randomUUID())
                .addValue("createdAt", CUTOFF.minus(10, ChronoUnit.DAYS).atOffset(ZoneOffset.UTC),
                        Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("published", published)
                .addValue("publishedAt", publishedAt == null ? null : publishedAt.atOffset(ZoneOffset.UTC),
                        Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("deadLettered", deadLettered);
        jdbc.update("""
                INSERT INTO store_outbox_events
                    (id, aggregate_id, topic, event_type, payload, created_at,
                     published, published_at, attempt_count, dead_lettered)
                VALUES
                    (:id, :aggregateId, 'store.events', 'RetentionTestEvent', '{}', :createdAt,
                     :published, :publishedAt, 0, :deadLettered)
                """, parameters);
        return id;
    }

    private UUID seedInbox(Instant processedAt, long recordOffset) {
        UUID eventId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO processed_kafka_events
                    (event_id, event_type, topic, partition_number, record_offset, processed_at)
                VALUES
                    (:eventId, 'RetentionTestEvent', 'retention.test', 0, :recordOffset, :processedAt)
                """, new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("recordOffset", recordOffset)
                .addValue("processedAt", processedAt.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE));
        return eventId;
    }

    private boolean outboxExists(UUID id) {
        return jdbc.queryForObject("select count(*) from store_outbox_events where id = :id",
                new MapSqlParameterSource("id", id), Long.class) == 1;
    }

    private boolean inboxExists(UUID id) {
        return jdbc.queryForObject("select count(*) from processed_kafka_events where event_id = :id",
                new MapSqlParameterSource("id", id), Long.class) == 1;
    }

    private long count(String table) {
        return jdbc.getJdbcTemplate().queryForObject("select count(*) from " + table, Long.class);
    }

    private long eligibleOutboxCountFromSeparateConnection() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "select count(*) from store_outbox_events where published = true and published_at < '"
                             + CUTOFF + "'")) {
            result.next();
            return result.getLong(1);
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
