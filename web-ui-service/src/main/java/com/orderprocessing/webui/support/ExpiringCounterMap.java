package com.orderprocessing.webui.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded in-process counters with a sliding expiry. This exists so abuse counting survives a Redis outage
 * instead of silently switching itself off; it is a degraded stand-in, not a replacement, because each instance
 * keeps its own tally.
 */
public class ExpiringCounterMap {
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final int maxEntries;
    private final Clock clock;

    public ExpiringCounterMap(int maxEntries, Clock clock) {
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    public long increment(String key, Duration window) {
        Instant now = clock.instant();
        purgeExpiredIfCrowded(now);
        Counter counter = counters.compute(key, (ignored, existing) -> {
            if (existing != null && !existing.hasExpired(now)) {
                existing.expiresAt = now.plus(window);
                return existing;
            }
            // Refuse unfamiliar keys once full rather than growing without bound; a flood of distinct usernames
            // must not become a memory exhaustion vector of its own.
            if (existing == null && counters.size() >= maxEntries) return null;
            return new Counter(now.plus(window));
        });
        return counter == null ? 0L : counter.value.incrementAndGet();
    }

    public long count(String key) {
        Counter counter = counters.get(key);
        if (counter == null) return 0L;
        if (counter.hasExpired(clock.instant())) {
            counters.remove(key, counter);
            return 0L;
        }
        return counter.value.get();
    }

    public void clear(String key) {
        counters.remove(key);
    }

    public int trackedKeys() {
        return counters.size();
    }

    private void purgeExpiredIfCrowded(Instant now) {
        if (counters.size() < maxEntries) return;
        counters.values().removeIf(counter -> counter.hasExpired(now));
    }

    private static final class Counter {
        private final AtomicLong value = new AtomicLong();
        private volatile Instant expiresAt;

        private Counter(Instant expiresAt) {
            this.expiresAt = expiresAt;
        }

        private boolean hasExpired(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }
}
