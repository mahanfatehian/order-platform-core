package com.orderprocessing.orderservice.config;

import com.orderprocessing.orderservice.model.Order;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

@Component
public class OrderMetrics implements MeterBinder {
    private final OrderMetricsSnapshot snapshot;

    public OrderMetrics(OrderMetricsSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (Order.Status status : Order.Status.values()) {
            Gauge.builder("order_platform_orders", snapshot, source -> source.ordersIn(status))
                    .description("Orders currently in each lifecycle state")
                    .tag("status", status.name())
                    .register(registry);
        }
        Gauge.builder("order_platform_outbox_pending", snapshot, OrderMetricsSnapshot::outboxPending)
                .description("Unpublished order-service outbox events")
                .tag("service", "order")
                .register(registry);
        Gauge.builder("order_platform_outbox_dead_lettered", snapshot, OrderMetricsSnapshot::outboxDeadLettered)
                .description("Dead-lettered order-service outbox events requiring intervention")
                .tag("service", "order")
                .register(registry);
    }
}
