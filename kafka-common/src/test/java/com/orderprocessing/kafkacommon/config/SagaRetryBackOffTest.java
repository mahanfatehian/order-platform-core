package com.orderprocessing.kafkacommon.config;

import org.junit.jupiter.api.Test;
import org.springframework.util.backoff.BackOffExecution;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A saga fact that exhausts its retries goes to the dead-letter topic and stops driving the saga - an
 * OrderCancelled that never lands leaves an inventory reservation held with nothing left to release it. The
 * retry window therefore has to outlast an ordinary transient fault, not just a momentary one.
 */
class SagaRetryBackOffTest {
    private static final long INITIAL_MS = 1000L;
    private static final double MULTIPLIER = 2.0d;
    private static final long MAX_INTERVAL_MS = 30_000L;
    private static final int MAX_ATTEMPTS = 8;

    @Test
    void theRetryWindowOutlastsAServiceRestart() {
        Window window = walk(KafkaConfig.sagaRetryBackOff(INITIAL_MS, MULTIPLIER, MAX_INTERVAL_MS, MAX_ATTEMPTS));

        assertThat(window.totalMillis)
                .describedAs("a transient database, Redis or peer-service fault needs longer than a few seconds")
                .isGreaterThanOrEqualTo(60_000L);
        assertThat(window.attempts).isEqualTo(MAX_ATTEMPTS);
    }

    @Test
    void theBackOffStillTerminatesAndIsCapped() {
        Window window = walk(KafkaConfig.sagaRetryBackOff(INITIAL_MS, MULTIPLIER, MAX_INTERVAL_MS, MAX_ATTEMPTS));

        assertThat(window.attempts)
                .describedAs("a broken record must not retry forever and stall its partition")
                .isLessThanOrEqualTo(12);
        assertThat(window.longestMillis)
                .describedAs("no single wait may exceed the configured ceiling")
                .isLessThanOrEqualTo(MAX_INTERVAL_MS);
    }

    @Test
    void nonsensicalSettingsCannotProduceAZeroOrNegativeWait() {
        Window window = walk(KafkaConfig.sagaRetryBackOff(0L, 0.1d, -5L, 0));

        assertThat(window.attempts).isGreaterThanOrEqualTo(1);
        assertThat(window.shortestMillis).isGreaterThan(0L);
    }

    private record Window(int attempts, long totalMillis, long longestMillis, long shortestMillis) { }

    private Window walk(org.springframework.util.backoff.BackOff backOff) {
        BackOffExecution execution = backOff.start();
        int attempts = 0;
        long total = 0L;
        long longest = 0L;
        long shortest = Long.MAX_VALUE;
        long next;
        while ((next = execution.nextBackOff()) != BackOffExecution.STOP) {
            attempts++;
            total += next;
            longest = Math.max(longest, next);
            shortest = Math.min(shortest, next);
            if (attempts > 100) {
                throw new AssertionError("back-off never stopped");
            }
        }
        return new Window(attempts, total, longest, shortest == Long.MAX_VALUE ? 0L : shortest);
    }
}
