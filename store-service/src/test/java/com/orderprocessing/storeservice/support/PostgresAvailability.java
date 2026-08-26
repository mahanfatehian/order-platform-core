package com.orderprocessing.storeservice.support;

import org.testcontainers.DockerClientFactory;

/**
 * Decides whether the PostgreSQL-backed integration tests can run at all.
 *
 * <p>They need a real database: one supplied through {@code -Dtask5.postgres.url}, or one Testcontainers starts
 * for them. Without either, the tests cannot prove anything, and failing them says the code is broken when the
 * truth is that the machine has no Docker. Reported as skipped instead, so a contributor without Docker still
 * gets a green build and CI, which has Docker, still runs them.
 */
public final class PostgresAvailability {
    private PostgresAvailability() {
    }

    public static boolean present() {
        if (System.getProperty("task5.postgres.url") != null) {
            return true;
        }
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException exception) {
            // A daemon that answers but refuses the handshake is as unusable here as no daemon at all.
            return false;
        }
    }
}
