package com.orderprocessing.storeservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.kafkacommon.event.OrderCancelledEvent;
import com.orderprocessing.kafkacommon.event.OrderDeliveredEvent;
import com.orderprocessing.kafkacommon.event.OrderFailedEvent;
import com.orderprocessing.kafkacommon.event.OrderPlacedEvent;
import com.orderprocessing.storeservice.model.Inventory;
import com.orderprocessing.storeservice.model.InventoryReservation;
import com.orderprocessing.storeservice.model.Product;
import com.orderprocessing.storeservice.model.StoreOutboxEvent;
import com.orderprocessing.storeservice.repository.InventoryRepository;
import com.orderprocessing.storeservice.repository.InventoryReservationRepository;
import com.orderprocessing.storeservice.repository.ProcessedKafkaEventRepository;
import com.orderprocessing.storeservice.repository.ProductRepository;
import com.orderprocessing.storeservice.repository.StoreOutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {
    @Mock private InventoryRepository inventoryRepository;
    @Mock private InventoryReservationRepository reservationRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProcessedKafkaEventRepository processedRepository;
    @Mock private StoreOutboxEventRepository outboxRepository;

    private InventoryService service;

    @BeforeEach
    void setUp() {
        service = new InventoryService(inventoryRepository, reservationRepository, productRepository,
                processedRepository, outboxRepository, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void reservesCompleteBatchAndWritesDurableReservationAndResultOutbox() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        OrderPlacedEvent event = placed(orderId, productId, 4);
        Product product = product(productId, true);
        Inventory stock = inventory(productId, 10, 1);
        when(processedRepository.insertIfAbsent(any(), anyString(), anyString(), anyInt(), anyLong())).thenReturn(1);
        when(reservationRepository.findByOrderIdForUpdate(orderId)).thenReturn(List.of());
        when(productRepository.findAllByIdOrdered(any())).thenReturn(List.of(product));
        when(inventoryRepository.findAllForUpdate(any())).thenReturn(List.of(stock));

        service.processOrderPlaced(event, "order.events", 0, 10L);

        assertThat(stock.getReservedQuantity()).isEqualTo(5);
        verify(reservationRepository).saveAll(any());
        ArgumentCaptor<StoreOutboxEvent> outbox = ArgumentCaptor.forClass(StoreOutboxEvent.class);
        verify(outboxRepository).save(outbox.capture());
        assertThat(outbox.getValue().getEventType()).isEqualTo("StockReservedEvent");
        assertThat(outbox.getValue().getAggregateId()).isEqualTo(orderId.toString());
    }

    @Test
    void insufficientBatchChangesNoInventoryAndWritesFailureResult() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        OrderPlacedEvent event = placed(orderId, productId, 20);
        Product product = product(productId, true);
        Inventory stock = inventory(productId, 10, 0);
        when(processedRepository.insertIfAbsent(any(), anyString(), anyString(), anyInt(), anyLong())).thenReturn(1);
        when(reservationRepository.findByOrderIdForUpdate(orderId)).thenReturn(List.of());
        when(productRepository.findAllByIdOrdered(any())).thenReturn(List.of(product));
        when(inventoryRepository.findAllForUpdate(any())).thenReturn(List.of(stock));

        service.processOrderPlaced(event, "order.events", 0, 11L);

        assertThat(stock.getReservedQuantity()).isZero();
        verify(reservationRepository, never()).saveAll(any());
        ArgumentCaptor<StoreOutboxEvent> outbox = ArgumentCaptor.forClass(StoreOutboxEvent.class);
        verify(outboxRepository).save(outbox.capture());
        assertThat(outbox.getValue().getEventType()).isEqualTo("StockInsufficientEvent");
        assertThat(outbox.getValue().getPayload()).contains("Insufficient inventory");
    }

    @Test
    void duplicateCancellationReleasesOwnedReservationOnlyOnce() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        OrderCancelledEvent event = new OrderCancelledEvent();
        event.setOrderId(orderId);
        InventoryReservation reservation = new InventoryReservation();
        reservation.setId(UUID.randomUUID());
        reservation.setOrderId(orderId);
        reservation.setProductId(productId);
        reservation.setQuantity(5);
        reservation.setStatus(InventoryReservation.Status.RESERVED);
        Inventory stock = inventory(productId, 10, 5);
        when(processedRepository.insertIfAbsent(any(), anyString(), anyString(), anyInt(), anyLong()))
                .thenReturn(1, 0);
        when(reservationRepository.findByOrderIdForUpdate(orderId)).thenReturn(List.of(reservation));
        when(inventoryRepository.findAllForUpdate(any())).thenReturn(List.of(stock));

        service.processOrderCancelled(event, "order.events", 0, 12L);
        service.processOrderCancelled(event, "order.events", 0, 12L);

        assertThat(stock.getReservedQuantity()).isZero();
        assertThat(reservation.getStatus()).isEqualTo(InventoryReservation.Status.RELEASED);
        verify(reservationRepository).saveAll(any());
    }

    @Test
    void deliveryConsumesReservationAndSettlesOnHandAndReservedQuantitiesTogether() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        OrderDeliveredEvent event = delivered(orderId);
        InventoryReservation reservation = reservation(orderId, productId, 4,
                InventoryReservation.Status.RESERVED);
        Inventory stock = inventory(productId, 10, 5);
        when(processedRepository.insertIfAbsent(any(), anyString(), anyString(), anyInt(), anyLong())).thenReturn(1);
        when(reservationRepository.findByOrderIdForUpdate(orderId)).thenReturn(List.of(reservation));
        when(inventoryRepository.findAllForUpdate(any())).thenReturn(List.of(stock));

        service.processOrderDelivered(event, "order.events", 0, 13L);

        assertThat(stock.getQuantity()).isEqualTo(6);
        assertThat(stock.getReservedQuantity()).isEqualTo(1);
        assertThat(stock.getAvailableQuantity()).isEqualTo(5);
        assertThat(reservation.getStatus()).isEqualTo(InventoryReservation.Status.CONSUMED);
        assertThat(reservation.getConsumedAt()).isNotNull();
        assertThat(reservation.getReleasedAt()).isNull();
        verify(inventoryRepository).saveAll(any());
        verify(reservationRepository).saveAll(any());
    }

    @Test
    void duplicateDeliveryEventCannotConsumeInventoryTwice() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        OrderDeliveredEvent event = delivered(orderId);
        InventoryReservation reservation = reservation(orderId, productId, 3,
                InventoryReservation.Status.RESERVED);
        Inventory stock = inventory(productId, 9, 3);
        when(processedRepository.insertIfAbsent(any(), anyString(), anyString(), anyInt(), anyLong()))
                .thenReturn(1, 0);
        when(reservationRepository.findByOrderIdForUpdate(orderId)).thenReturn(List.of(reservation));
        when(inventoryRepository.findAllForUpdate(any())).thenReturn(List.of(stock));

        service.processOrderDelivered(event, "order.events", 1, 20L);
        service.processOrderDelivered(event, "order.events", 1, 20L);

        assertThat(stock.getQuantity()).isEqualTo(6);
        assertThat(stock.getReservedQuantity()).isZero();
        assertThat(reservation.getStatus()).isEqualTo(InventoryReservation.Status.CONSUMED);
        verify(reservationRepository).saveAll(any());
        verify(inventoryRepository).saveAll(any());
    }

    @Test
    void cancellationAfterConsumptionLeavesSettledInventoryUnchanged() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        OrderCancelledEvent event = new OrderCancelledEvent();
        event.setOrderId(orderId);
        InventoryReservation consumed = reservation(orderId, productId, 2,
                InventoryReservation.Status.CONSUMED);
        consumed.setConsumedAt(Instant.now());
        when(processedRepository.insertIfAbsent(any(), anyString(), anyString(), anyInt(), anyLong())).thenReturn(1);
        when(reservationRepository.findByOrderIdForUpdate(orderId)).thenReturn(List.of(consumed));

        service.processOrderCancelled(event, "order.events", 2, 21L);

        assertThat(consumed.getStatus()).isEqualTo(InventoryReservation.Status.CONSUMED);
        assertThat(consumed.getReleasedAt()).isNull();
        verify(inventoryRepository, never()).findAllForUpdate(any());
        verify(inventoryRepository, never()).saveAll(any());
        verify(reservationRepository, never()).saveAll(any());
    }

    @Test
    void timedOutOrderReleasesReservationWithoutConsumingOnHandStock() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        OrderFailedEvent event = new OrderFailedEvent();
        event.setOrderId(orderId);
        InventoryReservation reservation = reservation(orderId, productId, 3,
                InventoryReservation.Status.RESERVED);
        Inventory stock = inventory(productId, 10, 3);
        when(processedRepository.insertIfAbsent(any(), anyString(), anyString(), anyInt(), anyLong())).thenReturn(1);
        when(reservationRepository.findByOrderIdForUpdate(orderId)).thenReturn(List.of(reservation));
        when(inventoryRepository.findAllForUpdate(any())).thenReturn(List.of(stock));

        service.processOrderFailed(event, "order.events", 1, 22L);

        assertThat(stock.getQuantity()).isEqualTo(10);
        assertThat(stock.getReservedQuantity()).isZero();
        assertThat(reservation.getStatus()).isEqualTo(InventoryReservation.Status.RELEASED);
        assertThat(reservation.getReleasedAt()).isNotNull();
    }

    private OrderPlacedEvent placed(UUID orderId, UUID productId, int quantity) {
        OrderPlacedEvent event = new OrderPlacedEvent();
        event.setOrderId(orderId);
        event.setItems(Map.of(productId, quantity));
        return event;
    }

    private OrderDeliveredEvent delivered(UUID orderId) {
        OrderDeliveredEvent event = new OrderDeliveredEvent();
        event.setOrderId(orderId);
        return event;
    }

    private InventoryReservation reservation(UUID orderId, UUID productId, int quantity,
                                             InventoryReservation.Status status) {
        InventoryReservation reservation = new InventoryReservation();
        reservation.setId(UUID.randomUUID());
        reservation.setOrderId(orderId);
        reservation.setProductId(productId);
        reservation.setQuantity(quantity);
        reservation.setStatus(status);
        reservation.setCreatedAt(Instant.now());
        reservation.setUpdatedAt(Instant.now());
        return reservation;
    }

    private Product product(UUID id, boolean active) {
        Product product = new Product();
        product.setId(id);
        product.setName("Product");
        product.setPrice(BigDecimal.TEN);
        product.setCategory(Product.Category.OTHER);
        product.setActive(active);
        product.setCreatedAt(Instant.now());
        product.setUpdatedAt(Instant.now());
        return product;
    }

    private Inventory inventory(UUID productId, int quantity, int reserved) {
        Inventory inventory = new Inventory();
        inventory.setProductId(productId);
        inventory.setQuantity(quantity);
        inventory.setReservedQuantity(reserved);
        inventory.setLastUpdated(Instant.now());
        return inventory;
    }
}
