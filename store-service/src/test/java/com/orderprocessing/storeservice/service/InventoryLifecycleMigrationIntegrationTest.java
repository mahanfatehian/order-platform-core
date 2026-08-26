package com.orderprocessing.storeservice.service;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIf(value = "com.orderprocessing.storeservice.support.PostgresAvailability#present",
        disabledReason = "Needs Docker for Testcontainers, or -Dtask5.postgres.url pointing at a database")
class InventoryLifecycleMigrationIntegrationTest {

    private static final String SCHEMA = "task5_v8_migration";
    private static final String EXTERNAL_POSTGRES_URL = System.getProperty("task5.postgres.url");
    private static final PostgreSQLContainer<?> POSTGRES = EXTERNAL_POSTGRES_URL == null
            ? new PostgreSQLContainer<>("postgres:16-alpine") : null;
    private static String jdbcUrl;
    private static String username;
    private static String password;

    @BeforeAll
    static void startPostgres() {
        if (POSTGRES != null) {
            POSTGRES.start();
            jdbcUrl = POSTGRES.getJdbcUrl();
            username = POSTGRES.getUsername();
            password = POSTGRES.getPassword();
            return;
        }
        jdbcUrl = EXTERNAL_POSTGRES_URL;
        username = System.getProperty("task5.postgres.username");
        password = System.getProperty("task5.postgres.password");
    }

    @AfterAll
    static void stopPostgres() {
        if (POSTGRES != null) {
            POSTGRES.stop();
        }
    }

    @Test
    void v8BackfillsLifecycleStateAndRejectsInvalidState() throws SQLException {
        migrateTo("7");
        Map<String, UUID> orders = seedReservationsBeforeV8();

        migrateTo(null);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            assertThat(lifecycleState(connection, orders.get("active"))).isEqualTo("ACTIVE");
            assertThat(lifecycleState(connection, orders.get("consumed"))).isEqualTo("CONSUMED");
            assertThat(lifecycleState(connection, orders.get("released"))).isEqualTo("RELEASED");
            assertThatThrownBy(() -> execute(connection,
                    "update " + SCHEMA + ".inventory_order_lifecycle set state = 'INVALID' where order_id = ?",
                    orders.get("active")))
                    .isInstanceOf(SQLException.class);
        }
    }

    private void migrateTo(String target) {
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .createSchemas(true)
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private Map<String, UUID> seedReservationsBeforeV8() throws SQLException {
        UUID productId = UUID.randomUUID();
        UUID activeOrder = UUID.randomUUID();
        UUID consumedOrder = UUID.randomUUID();
        UUID releasedOrder = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            execute(connection, "insert into " + SCHEMA + ".products "
                            + "(id, name, price, category, created_at, updated_at, sku, active) "
                            + "values (?, 'migration product', 1, 'OTHER', now(), now(), 'TASK5-V8', true)", productId);
            insertReservation(connection, activeOrder, productId, "RESERVED");
            insertReservation(connection, consumedOrder, productId, "CONSUMED");
            insertReservation(connection, releasedOrder, productId, "RELEASED");
        }
        return Map.of("active", activeOrder, "consumed", consumedOrder, "released", releasedOrder);
    }

    private void insertReservation(Connection connection, UUID orderId, UUID productId, String state) throws SQLException {
        String terminalColumn = "CONSUMED".equals(state) ? "consumed_at" : "RELEASED".equals(state) ? "released_at" : null;
        String sql = "insert into " + SCHEMA + ".inventory_reservations "
                + "(id, order_id, product_id, quantity, status, created_at, updated_at, released_at, consumed_at) "
                + "values (?, ?, ?, 1, ?, now(), now(), "
                + ("released_at".equals(terminalColumn) ? "now()" : "null") + ", "
                + ("consumed_at".equals(terminalColumn) ? "now()" : "null") + ")";
        execute(connection, sql, UUID.randomUUID(), orderId, productId, state);
    }

    private String lifecycleState(Connection connection, UUID orderId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select state from " + SCHEMA + ".inventory_order_lifecycle where order_id = ?")) {
            statement.setObject(1, orderId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private void execute(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            statement.executeUpdate();
        }
    }
}
