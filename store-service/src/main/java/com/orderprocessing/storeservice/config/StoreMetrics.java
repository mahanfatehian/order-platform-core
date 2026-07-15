package com.orderprocessing.storeservice.config;

import com.orderprocessing.storeservice.model.InventoryReservation;
import com.orderprocessing.storeservice.repository.InventoryReservationRepository;
import com.orderprocessing.storeservice.repository.StoreOutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

@Component
public class StoreMetrics implements MeterBinder {
    private final InventoryReservationRepository reservations;
    private final StoreOutboxEventRepository outbox;

    public StoreMetrics(InventoryReservationRepository reservations, StoreOutboxEventRepository outbox) {
        this.reservations = reservations;
        this.outbox = outbox;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (InventoryReservation.Status status : InventoryReservation.Status.values()) {
            Gauge.builder("order_platform_inventory_reservations", reservations,
                            repository -> repository.countByStatus(status))
                    .description("Inventory reservations currently in each state")
                    .tag("status", status.name())
                    .register(registry);
        }
        Gauge.builder("order_platform_outbox_pending", outbox,
                        StoreOutboxEventRepository::countByPublishedFalseAndDeadLetteredFalse)
                .description("Unpublished store-service outbox events")
                .tag("service", "store")
                .register(registry);
        Gauge.builder("order_platform_outbox_dead_lettered", outbox,
                        StoreOutboxEventRepository::countByDeadLetteredTrue)
                .description("Dead-lettered store-service outbox events requiring intervention")
                .tag("service", "store")
                .register(registry);
    }
}
