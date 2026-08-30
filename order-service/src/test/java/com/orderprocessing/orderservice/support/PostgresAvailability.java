package com.orderprocessing.orderservice.support;

import org.testcontainers.DockerClientFactory;

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
            return false;
        }
    }
}
