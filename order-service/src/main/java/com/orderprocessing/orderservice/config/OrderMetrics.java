package com.orderprocessing.orderservice.config;

import com.orderprocessing.orderservice.model.Order;
import com.orderprocessing.orderservice.repository.OrderRepository;
import com.orderprocessing.orderservice.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

@Component
public class OrderMetrics implements MeterBinder {
    private final OrderRepository orders;
    private final OutboxEventRepository outbox;

    public OrderMetrics(OrderRepository orders, OutboxEventRepository outbox) {
        this.orders = orders;
        this.outbox = outbox;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (Order.Status status : Order.Status.values()) {
            Gauge.builder("order_platform_orders", orders, repository -> repository.countByStatus(status))
                    .description("Orders currently in each lifecycle state")
                    .tag("status", status.name())
                    .register(registry);
        }
        Gauge.builder("order_platform_outbox_pending", outbox,
                        OutboxEventRepository::countByPublishedFalseAndDeadLetteredFalse)
                .description("Unpublished order-service outbox events")
                .tag("service", "order")
                .register(registry);
        Gauge.builder("order_platform_outbox_dead_lettered", outbox,
                        OutboxEventRepository::countByDeadLetteredTrue)
                .description("Dead-lettered order-service outbox events requiring intervention")
                .tag("service", "order")
                .register(registry);
    }
}
