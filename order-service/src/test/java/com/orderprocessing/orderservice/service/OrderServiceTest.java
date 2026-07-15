package com.orderprocessing.orderservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.kafkacommon.KafkaTopics;
import com.orderprocessing.kafkacommon.event.OrderDeliveredEvent;
import com.orderprocessing.kafkacommon.event.OrderPackagedEvent;
import com.orderprocessing.kafkacommon.event.OrderShippedEvent;
import com.orderprocessing.orderservice.client.StoreServiceClient;
import com.orderprocessing.orderservice.dto.CreateOrderRequest;
import com.orderprocessing.orderservice.dto.OrderItemRequest;
import com.orderprocessing.orderservice.dto.OrderResponse;
import com.orderprocessing.orderservice.dto.StoreQuoteItemResponse;
import com.orderprocessing.orderservice.dto.StoreQuoteResponse;
import com.orderprocessing.orderservice.exception.ForbiddenOperationException;
import com.orderprocessing.orderservice.exception.IdempotencyConflictException;
import com.orderprocessing.orderservice.exception.InvalidOrderStateException;
import com.orderprocessing.orderservice.exception.OrderTransitionConflictException;
import com.orderprocessing.orderservice.model.Order;
import com.orderprocessing.orderservice.model.OrderStatusHistory;
import com.orderprocessing.orderservice.model.OutboxEvent;
import com.orderprocessing.orderservice.repository.OrderRepository;
import com.orderprocessing.orderservice.repository.OrderStatusHistoryRepository;
import com.orderprocessing.orderservice.repository.OutboxEventRepository;
import com.orderprocessing.orderservice.repository.ProcessedKafkaEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock private OrderRepository orderRepository;
    @Mock private OutboxEventRepository outboxRepository;
    @Mock private ProcessedKafkaEventRepository processedRepository;
    @Mock private OrderStatusHistoryRepository historyRepository;
    @Mock private StoreServiceClient storeClient;

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, outboxRepository, processedRepository, historyRepository,
                storeClient, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void createsOrderFromAuthoritativeQuoteAndPersistsItemsWithPlacedOutbox() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(productId, 2)));
        when(orderRepository.findByUserIdAndIdempotencyKeyWithItems(userId, "checkout-1"))
                .thenReturn(Optional.empty());
        when(storeClient.quote(any())).thenReturn(new StoreQuoteResponse(List.of(
                new StoreQuoteItemResponse(productId, "Authoritative product", new BigDecimal("12.50"),
                        true, 2, 10, true))));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = service.createOrder(userId, request, "checkout-1", "corr-1");

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order stored = orderCaptor.getValue();
        assertThat(stored.getTotalAmount()).isEqualByComparingTo("25.00");
        assertThat(stored.getStatus()).isEqualTo(Order.Status.PENDING);
        assertThat(stored.getItems()).hasSize(1);
        assertThat(stored.getItems().getFirst().getProductName()).isEqualTo("Authoritative product");
        assertThat(stored.getItems().getFirst().getUnitPrice()).isEqualByComparingTo("12.50");
        assertThat(stored.getItems().getFirst().getOrder()).isSameAs(stored);
        assertThat(response.getTotalAmount()).isEqualByComparingTo("25.00");

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("OrderPlacedEvent");
        assertThat(outboxCaptor.getValue().getTopic()).isEqualTo("order.events");
        assertThat(outboxCaptor.getValue().getPayload()).contains("corr-1", productId.toString());
    }

    @Test
    void rejectsAccessToAnotherUsersOrder() {
        UUID owner = UUID.randomUUID();
        Order order = order(owner, Order.Status.CONFIRMED);
        when(orderRepository.findOrderWithItemsById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.getOrder(order.getId(), UUID.randomUUID(), false))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void fulfillmentQueuesAreRestrictedByOperationalRoleAndStatus() {
        assertThatThrownBy(() -> service.getFulfillmentOrders(
                Order.Status.PACKAGED, Set.of("ROLE_WAREHOUSE"),
                org.springframework.data.domain.PageRequest.of(0, 20)))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("cannot view the PACKAGED");

        assertThatThrownBy(() -> service.getFulfillmentOrders(
                Order.Status.CONFIRMED, Set.of("ROLE_ADMIN"),
                org.springframework.data.domain.PageRequest.of(0, 20)))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("fulfillment role is required");
    }

    @Test
    void repeatedCancellationOfAlreadyCancelledOrderDoesNotEmitAnotherEvent() {
        UUID owner = UUID.randomUUID();
        Order order = order(owner, Order.Status.CANCELLED);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        OrderResponse response = service.cancelOrder(order.getId(), owner, false, "corr");

        assertThat(response.getStatus()).isEqualTo("CANCELLED");
        verify(orderRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void cancellationIsStrictlyBlockedOnceWarehouseFulfillmentStarts() {
        UUID owner = UUID.randomUUID();
        for (Order.Status status : List.of(
                Order.Status.PACKAGED, Order.Status.SHIPPED, Order.Status.DELIVERED)) {
            Order order = order(owner, status);
            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.cancelOrder(order.getId(), owner, false, "corr"))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("cannot be cancelled");
        }

        verify(outboxRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void advancesOrderThroughAuthenticatedCommandsAndEmitsActorAwareFacts() {
        UUID owner = UUID.randomUUID();
        UUID warehouseWorker = UUID.randomUUID();
        UUID deliveryDriver = UUID.randomUUID();
        Order order = order(owner, Order.Status.CONFIRMED);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        service.packOrder(order.getId(), warehouseWorker, "corr-pack");
        assertThat(order.getStatus()).isEqualTo(Order.Status.PACKAGED);

        service.shipOrder(order.getId(), deliveryDriver, "corr-ship", "TRACK-123");
        assertThat(order.getStatus()).isEqualTo(Order.Status.SHIPPED);
        assertThat(order.getTrackingReference()).isEqualTo("TRACK-123");

        service.deliverOrder(order.getId(), deliveryDriver, "corr-deliver");
        assertThat(order.getStatus()).isEqualTo(Order.Status.DELIVERED);

        ArgumentCaptor<OutboxEvent> events = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository, org.mockito.Mockito.times(3)).save(events.capture());
        assertThat(events.getAllValues()).extracting(OutboxEvent::getEventType)
                .containsExactly("OrderPackagedEvent", "OrderShippedEvent", "OrderDeliveredEvent");
        assertThat(events.getAllValues().get(0).getPayload())
                .contains(warehouseWorker.toString(), "ROLE_WAREHOUSE", "corr-pack", "CONFIRMED", "PACKAGED");
        assertThat(events.getAllValues().get(1).getPayload())
                .contains(deliveryDriver.toString(), "ROLE_DELIVERY", "TRACK-123", "PACKAGED", "SHIPPED");
        ArgumentCaptor<OrderStatusHistory> history = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(historyRepository, org.mockito.Mockito.times(3)).save(history.capture());
        assertThat(history.getAllValues()).extracting(OrderStatusHistory::getActorRole)
                .containsExactly("ROLE_WAREHOUSE", "ROLE_DELIVERY", "ROLE_DELIVERY");
        assertThat(history.getAllValues()).extracting(OrderStatusHistory::getFromStatus)
                .containsExactly(Order.Status.CONFIRMED, Order.Status.PACKAGED, Order.Status.SHIPPED);
        assertThat(history.getAllValues()).extracting(OrderStatusHistory::getToStatus)
                .containsExactly(Order.Status.PACKAGED, Order.Status.SHIPPED, Order.Status.DELIVERED);
        assertThat(history.getAllValues()).extracting(OrderStatusHistory::getEventId)
                .containsExactlyElementsOf(events.getAllValues().stream().map(OutboxEvent::getId).toList());
    }

    @Test
    void rejectsOutOfOrderHumanCommandWithPreciseConflict() {
        UUID owner = UUID.randomUUID();
        Order order = order(owner, Order.Status.CONFIRMED);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.shipOrder(order.getId(), UUID.randomUUID(), "corr", null))
                .isInstanceOf(OrderTransitionConflictException.class)
                .hasMessageContaining("expected status PACKAGED")
                .hasMessageContaining("current status is CONFIRMED");

        assertThat(order.getStatus()).isEqualTo(Order.Status.CONFIRMED);
        verify(outboxRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void consumesPublishedFactsWithoutUsingKafkaAsACommandChannel() {
        Order order = order(UUID.randomUUID(), Order.Status.CONFIRMED);
        when(processedRepository.insertIfAbsent(any(), anyString(), anyString(), anyInt(), anyLong()))
                .thenReturn(1);

        OrderPackagedEvent packaged = new OrderPackagedEvent();
        packaged.setOrderId(order.getId());
        service.processOrderPackaged(packaged, KafkaTopics.ORDER_EVENTS, 2, 30L);

        assertThat(order.getStatus()).isEqualTo(Order.Status.CONFIRMED);
        verify(orderRepository, never()).findByIdForUpdate(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void ignoresDuplicatePublishedFactBeforeLoadingOrder() {
        when(processedRepository.insertIfAbsent(any(), anyString(), anyString(), anyInt(), anyLong()))
                .thenReturn(0);

        OrderPackagedEvent packaged = new OrderPackagedEvent();
        packaged.setOrderId(UUID.randomUUID());
        service.processOrderPackaged(packaged, KafkaTopics.ORDER_EVENTS, 2, 30L);

        verify(orderRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void exactCommandReplayIsIdempotentButCannotChangeShipmentIdentity() {
        Order order = order(UUID.randomUUID(), Order.Status.SHIPPED);
        order.setTrackingReference("TRACK-123");
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        OrderResponse replay = service.shipOrder(order.getId(), UUID.randomUUID(), "retry", "TRACK-123");

        assertThat(replay.getStatus()).isEqualTo("SHIPPED");
        verify(outboxRepository, never()).save(any());
        verify(historyRepository, never()).save(any());

        assertThatThrownBy(() -> service.shipOrder(
                order.getId(), UUID.randomUUID(), "retry-2", "DIFFERENT"))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("different tracking reference");
    }

    @Test
    void stalePendingOrderFailsAndEmitsCompensationFact() {
        Order order = order(UUID.randomUUID(), Order.Status.PENDING);
        when(orderRepository.lockStalePendingOrders(any(), anyInt())).thenReturn(List.of(order));

        int reconciled = service.failStalePendingOrders(Instant.now(), 25);

        assertThat(reconciled).isEqualTo(1);
        assertThat(order.getStatus()).isEqualTo(Order.Status.FAILED);
        assertThat(order.getFailureReason()).contains("timed out");
        ArgumentCaptor<OutboxEvent> outbox = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(outbox.capture());
        assertThat(outbox.getValue().getEventType()).isEqualTo("OrderFailedEvent");
        assertThat(outbox.getValue().getPayload()).contains("timed out");
        ArgumentCaptor<OrderStatusHistory> history = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getActorRole()).isEqualTo("SYSTEM_RECONCILIATION");
        assertThat(history.getValue().getToStatus()).isEqualTo(Order.Status.FAILED);
    }

    private Order order(UUID owner, Order.Status status) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setUserId(owner);
        order.setStatus(status);
        order.setTotalAmount(BigDecimal.TEN);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        order.setItems(List.of());
        return order;
    }
}
