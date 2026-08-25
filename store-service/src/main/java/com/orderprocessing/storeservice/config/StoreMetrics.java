package com.orderprocessing.storeservice.config;

import com.orderprocessing.storeservice.model.InventoryReservation;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

@Component
public class StoreMetrics implements MeterBinder {
    private final StoreMetricsSnapshot snapshot;

    public StoreMetrics(StoreMetricsSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (InventoryReservation.Status status : InventoryReservation.Status.values()) {
            Gauge.builder("order_platform_inventory_reservations", snapshot,
                            source -> source.reservationsIn(status))
                    .description("Inventory reservations currently in each state")
                    .tag("status", status.name())
                    .register(registry);
        }
        Gauge.builder("order_platform_outbox_pending", snapshot, StoreMetricsSnapshot::outboxPending)
                .description("Unpublished store-service outbox events")
                .tag("service", "store")
                .register(registry);
        Gauge.builder("order_platform_outbox_dead_lettered", snapshot, StoreMetricsSnapshot::outboxDeadLettered)
                .description("Dead-lettered store-service outbox events requiring intervention")
                .tag("service", "store")
                .register(registry);
    }
}
