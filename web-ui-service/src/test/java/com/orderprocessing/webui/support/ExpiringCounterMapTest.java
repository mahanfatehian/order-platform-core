package com.orderprocessing.webui.support;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ExpiringCounterMapTest {
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-19T10:00:00Z"));
    private final ExpiringCounterMap counters = new ExpiringCounterMap(3, clock);

    @Test
    void accumulatesRepeatedEventsAgainstOneKey() {
        counters.increment("a", WINDOW);
        counters.increment("a", WINDOW);

        assertThat(counters.increment("a", WINDOW)).isEqualTo(3L);
        assertThat(counters.count("a")).isEqualTo(3L);
    }

    @Test
    void forgetsACounterOnceTheWindowElapses() {
        counters.increment("a", WINDOW);
        clock.advance(WINDOW);

        assertThat(counters.count("a")).isZero();
        assertThat(counters.increment("a", WINDOW)).isEqualTo(1L);
    }

    @Test
    void slidesTheWindowOnEachEventSoSustainedPressureNeverLapses() {
        counters.increment("a", WINDOW);
        clock.advance(WINDOW.minusSeconds(1));
        counters.increment("a", WINDOW);
        clock.advance(WINDOW.minusSeconds(1));

        assertThat(counters.count("a")).isEqualTo(2L);
    }

    @Test
    void clearingRemovesTheCounterOutright() {
        counters.increment("a", WINDOW);
        counters.clear("a");

        assertThat(counters.count("a")).isZero();
    }

    @Test
    void refusesUnfamiliarKeysOnceFullSoAFloodCannotExhaustMemory() {
        counters.increment("a", WINDOW);
        counters.increment("b", WINDOW);
        counters.increment("c", WINDOW);

        assertThat(counters.increment("d", WINDOW)).isZero();
        assertThat(counters.trackedKeys()).isEqualTo(3);
        // Keys already being tracked keep counting, so an established abuser is not helped by the flood.
        assertThat(counters.increment("a", WINDOW)).isEqualTo(2L);
    }

    @Test
    void reclaimsExpiredKeysBeforeRefusingNewOnes() {
        counters.increment("a", WINDOW);
        counters.increment("b", WINDOW);
        counters.increment("c", WINDOW);
        clock.advance(WINDOW);

        assertThat(counters.increment("d", WINDOW)).isEqualTo(1L);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration amount) {
            instant = instant.plus(amount);
        }

        @Override public Instant instant() { return instant; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }
}
