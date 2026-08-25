package com.orderprocessing.orderservice.config;

import com.orderprocessing.orderservice.model.Order;
import com.orderprocessing.orderservice.repository.OrderRepository;
import com.orderprocessing.orderservice.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Holds the gauge values behind a short-lived cache.
 *
 * <p>Micrometer invokes a gauge every time the endpoint is scraped, so reading the database inside the gauge
 * makes scrape frequency, not application load, decide how often those queries run. This refreshes at most once
 * per TTL no matter how many gauges or scrapers ask.
 */
@Component
@Slf4j
public class OrderMetricsSnapshot {
    private final OrderRepository orders;
    private final OutboxEventRepository outbox;
    private final Duration ttl;
    private final AtomicReference<Snapshot> current = new AtomicReference<>(Snapshot.empty());
    private final ReentrantLock refreshing = new ReentrantLock();

    public OrderMetricsSnapshot(OrderRepository orders,
                                OutboxEventRepository outbox,
                                @Value("${metrics.snapshot-ttl:30s}") Duration ttl) {
        this.orders = orders;
        this.outbox = outbox;
        this.ttl = ttl;
    }

    public long ordersIn(Order.Status status) {
        return current().orders().getOrDefault(status, 0L);
    }

    public long outboxPending() {
        return current().outboxPending();
    }

    public long outboxDeadLettered() {
        return current().outboxDeadLettered();
    }

    private Snapshot current() {
        Snapshot existing = current.get();
        if (existing.freshAt(Instant.now(), ttl)) {
            return existing;
        }
        // One caller refreshes and the rest serve the previous values. Queueing every concurrent scrape on the
        // database would reintroduce exactly the load this class exists to remove.
        if (!refreshing.tryLock()) {
            return existing;
        }
        try {
            Snapshot latest = current.get();
            if (latest.freshAt(Instant.now(), ttl)) {
                return latest;
            }
            Snapshot refreshed = read();
            current.set(refreshed);
            return refreshed;
        } catch (RuntimeException exception) {
            // Metrics must never be the reason a scrape fails; the previous values are stale but harmless.
            log.warn("Order metric snapshot refresh failed; serving the previous values", exception);
            return existing;
        } finally {
            refreshing.unlock();
        }
    }

    private Snapshot read() {
        Map<Order.Status, Long> byStatus = new EnumMap<>(Order.Status.class);
        orders.countGroupedByStatus().forEach(row -> byStatus.put(row.getStatus(), row.getTotal()));
        return new Snapshot(byStatus,
                outbox.countByPublishedFalseAndDeadLetteredFalse(),
                outbox.countByDeadLetteredTrue(),
                Instant.now());
    }

    private record Snapshot(Map<Order.Status, Long> orders, long outboxPending, long outboxDeadLettered,
                            Instant takenAt) {
        private static Snapshot empty() {
            return new Snapshot(Map.of(), 0L, 0L, Instant.EPOCH);
        }

        private boolean freshAt(Instant now, Duration ttl) {
            return now.isBefore(takenAt.plus(ttl));
        }
    }
}
