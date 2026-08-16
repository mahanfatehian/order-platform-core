package com.orderprocessing.storeservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.kafkacommon.event.OrderCancelledEvent;
import com.orderprocessing.kafkacommon.event.OrderDeliveredEvent;
import com.orderprocessing.kafkacommon.event.OrderPlacedEvent;
import com.orderprocessing.storeservice.model.InventoryOrderLifecycle;
import com.orderprocessing.storeservice.repository.InventoryOrderLifecycleRepository;
import com.orderprocessing.storeservice.repository.InventoryRepository;
import com.orderprocessing.storeservice.repository.InventoryReservationRepository;
import com.orderprocessing.storeservice.repository.ProcessedKafkaEventRepository;
import com.orderprocessing.storeservice.repository.ProductRepository;
import com.orderprocessing.storeservice.repository.StoreOutboxEventRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryLifecycleGuardTest {

    @Test
    void compensationBeforePlacementRetainsReleasedTombstoneAndSkipsReservation() {
        InventoryRepository inventory = mock(InventoryRepository.class);
        InventoryReservationRepository reservations = mock(InventoryReservationRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        ProcessedKafkaEventRepository inbox = mock(ProcessedKafkaEventRepository.class);
        StoreOutboxEventRepository outbox = mock(StoreOutboxEventRepository.class);
        InventoryOrderLifecycleRepository lifecycleRepository = mock(InventoryOrderLifecycleRepository.class);
        InventoryService service = new InventoryService(inventory, lifecycleRepository, reservations, products, inbox,
                outbox, new ObjectMapper());
        UUID orderId = UUID.randomUUID();
        AtomicReference<InventoryOrderLifecycle> lifecycle = new AtomicReference<>(lifecycle(orderId,
                InventoryOrderLifecycle.State.RELEASED));
        when(inbox.insertIfAbsent(any(), anyString(), anyString(), anyInt(), anyLong())).thenReturn(1);
        when(lifecycleRepository.findByOrderIdForUpdate(orderId)).thenAnswer(ignored -> Optional.of(lifecycle.get()));

        OrderCancelledEvent cancelled = new OrderCancelledEvent();
        cancelled.setOrderId(orderId);
        service.processOrderCancelled(cancelled, "order.events", 0, 1);

        OrderPlacedEvent placed = new OrderPlacedEvent();
        placed.setOrderId(orderId);
        placed.setItems(Map.of(UUID.randomUUID(), 1));
        service.processOrderPlaced(placed, "order.events", 0, 2);

        assertThat(lifecycle.get().getState()).isEqualTo(InventoryOrderLifecycle.State.RELEASED);
        verify(reservations, never()).saveAll(any());
        verify(products, never()).findAllByIdOrdered(any());
    }

    @Test
    void latePlacementAfterConsumedLifecycleIsIgnored() {
        InventoryRepository inventory = mock(InventoryRepository.class);
        InventoryReservationRepository reservations = mock(InventoryReservationRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        ProcessedKafkaEventRepository inbox = mock(ProcessedKafkaEventRepository.class);
        StoreOutboxEventRepository outbox = mock(StoreOutboxEventRepository.class);
        InventoryOrderLifecycleRepository lifecycleRepository = mock(InventoryOrderLifecycleRepository.class);
        InventoryService service = new InventoryService(inventory, lifecycleRepository, reservations, products, inbox,
                outbox, new ObjectMapper());
        UUID orderId = UUID.randomUUID();
        when(inbox.insertIfAbsent(any(), anyString(), anyString(), anyInt(), anyLong())).thenReturn(1);
        when(lifecycleRepository.findByOrderIdForUpdate(orderId))
                .thenReturn(Optional.of(lifecycle(orderId, InventoryOrderLifecycle.State.CONSUMED)));

        OrderPlacedEvent placed = new OrderPlacedEvent();
        placed.setOrderId(orderId);
        placed.setItems(Map.of(UUID.randomUUID(), 1));
        service.processOrderPlaced(placed, "order.events", 0, 3);

        verify(reservations, never()).saveAll(any());
        verify(products, never()).findAllByIdOrdered(any());
    }

    @Test
    void laterDeliveryAfterConsumedLifecycleDoesNotConsumeAgain() {
        InventoryRepository inventory = mock(InventoryRepository.class);
        InventoryReservationRepository reservations = mock(InventoryReservationRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        ProcessedKafkaEventRepository inbox = mock(ProcessedKafkaEventRepository.class);
        StoreOutboxEventRepository outbox = mock(StoreOutboxEventRepository.class);
        InventoryOrderLifecycleRepository lifecycleRepository = mock(InventoryOrderLifecycleRepository.class);
        InventoryService service = new InventoryService(inventory, lifecycleRepository, reservations, products, inbox,
                outbox, new ObjectMapper());
        UUID orderId = UUID.randomUUID();
        when(inbox.insertIfAbsent(any(), anyString(), anyString(), anyInt(), anyLong())).thenReturn(1);
        when(lifecycleRepository.findByOrderIdForUpdate(orderId))
                .thenReturn(Optional.of(lifecycle(orderId, InventoryOrderLifecycle.State.CONSUMED)));

        OrderDeliveredEvent delivered = new OrderDeliveredEvent();
        delivered.setOrderId(orderId);
        service.processOrderDelivered(delivered, "order.events", 0, 4);

        verify(reservations, never()).findByOrderIdForUpdate(any());
        verify(inventory, never()).findAllForUpdate(any());
        verify(inventory, never()).saveAll(any());
    }

    private static InventoryOrderLifecycle lifecycle(UUID orderId, InventoryOrderLifecycle.State state) {
        InventoryOrderLifecycle lifecycle = new InventoryOrderLifecycle();
        lifecycle.setOrderId(orderId);
        lifecycle.setState(state);
        lifecycle.setLastEventId(UUID.randomUUID());
        lifecycle.setUpdatedAt(Instant.now());
        return lifecycle;
    }
}
