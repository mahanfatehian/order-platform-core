package com.orderprocessing.orderservice.config;

import com.orderprocessing.orderservice.model.Order;
import com.orderprocessing.orderservice.repository.OrderRepository;
import com.orderprocessing.orderservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderMetricsSnapshotTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OutboxEventRepository outbox = mock(OutboxEventRepository.class);

    private static OrderRepository.StatusCount count(Order.Status status, long total) {
        return new OrderRepository.StatusCount() {
            @Override public Order.Status getStatus() { return status; }
            @Override public long getTotal() { return total; }
        };
    }

    @Test
    void readsTheDatabaseOncePerTtlNoMatterHowManyGaugesAsk() {
        when(orders.countGroupedByStatus())
                .thenReturn(List.of(count(Order.Status.PENDING, 4L), count(Order.Status.DELIVERED, 9L)));
        OrderMetricsSnapshot snapshot = new OrderMetricsSnapshot(orders, outbox, Duration.ofMinutes(5));

        for (Order.Status status : Order.Status.values()) {
            snapshot.ordersIn(status);
        }
        snapshot.outboxPending();
        snapshot.outboxDeadLettered();

        // Nine gauge reads, the number a single scrape performs, against one grouped query and two counts.
        verify(orders, times(1)).countGroupedByStatus();
        verify(outbox, times(1)).countByPublishedFalseAndDeadLetteredFalse();
        verify(outbox, times(1)).countByDeadLetteredTrue();
    }

    @Test
    void reportsTheGroupedCountsAndTreatsAnAbsentStatusAsZero() {
        when(orders.countGroupedByStatus()).thenReturn(List.of(count(Order.Status.PENDING, 4L)));
        when(outbox.countByPublishedFalseAndDeadLetteredFalse()).thenReturn(2L);
        when(outbox.countByDeadLetteredTrue()).thenReturn(1L);
        OrderMetricsSnapshot snapshot = new OrderMetricsSnapshot(orders, outbox, Duration.ofMinutes(5));

        assertThat(snapshot.ordersIn(Order.Status.PENDING)).isEqualTo(4L);
        assertThat(snapshot.ordersIn(Order.Status.CANCELLED)).isZero();
        assertThat(snapshot.outboxPending()).isEqualTo(2L);
        assertThat(snapshot.outboxDeadLettered()).isEqualTo(1L);
    }

    @Test
    void refreshesAgainOnceTheSnapshotHasExpired() {
        when(orders.countGroupedByStatus()).thenReturn(List.of(count(Order.Status.PENDING, 4L)));
        OrderMetricsSnapshot snapshot = new OrderMetricsSnapshot(orders, outbox, Duration.ZERO);

        snapshot.ordersIn(Order.Status.PENDING);
        snapshot.ordersIn(Order.Status.PENDING);

        verify(orders, times(2)).countGroupedByStatus();
    }

    @Test
    void servesThePreviousValuesWhenARefreshFails() {
        when(orders.countGroupedByStatus())
                .thenReturn(List.of(count(Order.Status.PENDING, 4L)))
                .thenThrow(new IllegalStateException("database unreachable"));
        OrderMetricsSnapshot snapshot = new OrderMetricsSnapshot(orders, outbox, Duration.ZERO);

        assertThat(snapshot.ordersIn(Order.Status.PENDING)).isEqualTo(4L);
        // A scrape must not fail because the database is briefly unavailable.
        assertThat(snapshot.ordersIn(Order.Status.PENDING)).isEqualTo(4L);
    }
}
