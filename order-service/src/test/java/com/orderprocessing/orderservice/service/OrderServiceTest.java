package com.orderprocessing.orderservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.kafkacommon.KafkaTopics;
import com.orderprocessing.kafkacommon.config.KafkaTopicNames;
import com.orderprocessing.kafkacommon.event.OrderCancelledEvent;
import com.orderprocessing.kafkacommon.event.OrderConfirmedEvent;
import com.orderprocessing.kafkacommon.event.RefundPostedEvent;
import com.orderprocessing.kafkacommon.event.OrderDeliveredEvent;
import com.orderprocessing.kafkacommon.event.OrderPackagedEvent;
import com.orderprocessing.kafkacommon.event.OrderShippedEvent;
import com.orderprocessing.orderservice.client.AddressBookClient;
import com.orderprocessing.orderservice.client.StoreServiceClient;
import com.orderprocessing.orderservice.client.WalletLedgerClient;
import com.orderprocessing.orderservice.dto.CreateOrderRequest;
import com.orderprocessing.orderservice.dto.OrderItemRequest;
import com.orderprocessing.orderservice.dto.OrderResponse;
import com.orderprocessing.orderservice.dto.StoreQuoteItemResponse;
import com.orderprocessing.orderservice.dto.StoreQuoteResponse;
import com.orderprocessing.orderservice.exception.ForbiddenOperationException;
import com.orderprocessing.orderservice.exception.IdempotencyConflictException;
import com.orderprocessing.orderservice.exception.InvalidOrderStateException;
import com.orderprocessing.orderservice.exception.OrderTransitionConflictException;
import com.orderprocessing.kafkacommon.event.StockInsufficientEvent;
import com.orderprocessing.kafkacommon.event.StockReservedEvent;
import com.orderprocessing.orderservice.exception.InsufficientWalletBalanceException;
import com.orderprocessing.orderservice.exception.ResourceNotFoundException;
import com.orderprocessing.orderservice.exception.ServiceUnavailableException;
import com.orderprocessing.orderservice.model.Order;
import com.orderprocessing.orderservice.model.OrderItem;
import com.orderprocessing.orderservice.model.OrderStatusHistory;
import com.orderprocessing.orderservice.model.OutboxEvent;
import com.orderprocessing.orderservice.repository.OrderRepository;
import com.orderprocessing.orderservice.repository.OrderStatusHistoryRepository;
import com.orderprocessing.orderservice.repository.OutboxEventRepository;
import com.orderprocessing.orderservice.repository.ProcessedKafkaEventRepository;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock OrderRepository orderRepository;
    @Mock OutboxEventRepository outboxRepository;
    @Mock ProcessedKafkaEventRepository processedRepository;
    @Mock OrderStatusHistoryRepository historyRepository;
    @Mock StoreServiceClient storeClient;
    @Mock WalletLedgerClient walletClient;
    @Mock AddressBookClient addressBookClient;
    @Mock TransactionOperations orderTransactions;

    private static final UUID ADDRESS_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, outboxRepository, processedRepository, historyRepository,
                storeClient, walletClient, addressBookClient, new ObjectMapper().findAndRegisterModules(),
                orderTransactions, new KafkaTopicNames("orders.v2", "stores.v2"));
        lenient().when(walletClient.placeHold(any(), any(), anyLong(), any(), any()))
                .thenReturn(new WalletLedgerClient.HoldPlaced(UUID.randomUUID(), 1));
        lenient().when(addressBookClient.findOwned(any(), any())).thenReturn(shipTo());
    }

    @Test
    void createsOrderFromAuthoritativeQuoteAndPersistsItemsWithPlacedOutbox() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        CreateOrderRequest request = checkout(productId, 2);
        when(orderTransactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
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
        assertThat(stored.getShipRecipient()).isEqualTo("Ada Lovelace");
        assertThat(stored.getShipLine1()).isEqualTo("12 Enghelab");
        assertThat(stored.getShipLine2()).isEqualTo("Unit 4");
        assertThat(stored.getShipCity()).isEqualTo("Tehran");
        assertThat(stored.getShipCountry()).isEqualTo("IR");
        assertThat(response.getShipRecipient()).isEqualTo("Ada Lovelace");

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("OrderPlacedEvent");
        assertThat(outboxCaptor.getValue().getTopic()).isEqualTo("orders.v2");
        assertThat(outboxCaptor.getValue().getPayload()).contains("corr-1", productId.toString());
    }

    @Test
    void rejectsReplayWithDifferentOrderPayload() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Order existing = order(userId, Order.Status.PENDING);
        existing.setIdempotencyKey("checkout-1");
        existing.setItems(List.of(orderItem(existing, productId, 1)));
        CreateOrderRequest changedRequest = new CreateOrderRequest(List.of(new OrderItemRequest(productId, 2)));
        when(orderRepository.findByUserIdAndIdempotencyKeyWithItems(userId, "checkout-1"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createOrder(userId, changedRequest, "checkout-1", "corr"))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("different order payload");

        verifyNoInteractions(storeClient);
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void doesNotAcquireIdempotencyLockWhenAuthoritativeQuoteFails() {
        UUID userId = UUID.randomUUID();
        CreateOrderRequest request = checkout(UUID.randomUUID(), 1);
        when(storeClient.quote(any())).thenThrow(new FeignException.ServiceUnavailable(
                "unavailable", mock(Request.class), null, Map.of()));

        assertThatThrownBy(() -> service.createOrder(userId, request, "checkout-2", "corr"))
                .isInstanceOf(ServiceUnavailableException.class);

        verify(orderRepository, never()).acquireIdempotencyLock(anyString());
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
        verify(walletClient, never()).refundCapture(any(), any(), any());
        verify(walletClient, never()).releaseHold(any(), any(), any());
    }

    @Test
    void cancellationIsStrictlyBlockedOnceWarehouseFulfillmentStarts() {
        UUID owner = UUID.randomUUID();
        for (Order.Status status : List.of(
                Order.Status.PACKAGED, Order.Status.SHIPPED, Order.Status.DELIVERED)) {
            Order order = order(owner, status);
            order.setWalletCaptureEntryId(UUID.randomUUID());
            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.cancelOrder(order.getId(), owner, false, "corr"))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("cannot be cancelled");
            assertThat(order.getStatus()).isEqualTo(status);
        }

        verify(outboxRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
        verify(walletClient, never()).refundCapture(any(), any(), any());
        verify(walletClient, never()).releaseHold(any(), any(), any());
    }

    @Test
    void cancellingAConfirmedOrderRefundsTheCaptureOnceAndStillReleasesStock() throws Exception {
        UUID owner = UUID.randomUUID();
        Order order = order(owner, Order.Status.CONFIRMED);
        UUID headphones = UUID.randomUUID();
        UUID hub = UUID.randomUUID();
        order.setItems(List.of(orderItem(order, headphones, 2), orderItem(order, hub, 3)));
        order.setWalletHoldId(UUID.randomUUID());
        order.setWalletCaptureEntryId(UUID.randomUUID());
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(walletClient.refundCapture(order.getWalletCaptureEntryId(), order.getId() + ":refund", "corr"))
                .thenReturn(UUID.randomUUID());

        OrderResponse response = service.cancelOrder(order.getId(), owner, false, "corr");

        assertThat(response.getStatus()).isEqualTo("CANCELLED");
        assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED);
        verify(walletClient).refundCapture(order.getWalletCaptureEntryId(), order.getId() + ":refund", "corr");
        verify(walletClient, never()).releaseHold(any(), any(), any());
        ArgumentCaptor<OutboxEvent> outbox = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository, times(2)).save(outbox.capture());
        assertThat(outbox.getAllValues()).extracting(OutboxEvent::getEventType)
                .containsExactly("OrderCancelledEvent", "RefundPostedEvent");
        OrderCancelledEvent fact = new ObjectMapper().findAndRegisterModules()
                .readValue(outbox.getAllValues().get(0).getPayload(), OrderCancelledEvent.class);
        assertThat(fact.getItems()).containsExactlyInAnyOrderEntriesOf(Map.of(headphones, 2, hub, 3));
        RefundPostedEvent refund = new ObjectMapper().findAndRegisterModules()
                .readValue(outbox.getAllValues().get(1).getPayload(), RefundPostedEvent.class);
        assertThat(refund.getOrderId()).isEqualTo(order.getId());
        assertThat(refund.getUserId()).isEqualTo(owner);
        assertThat(refund.getRefundEntryId()).isNotNull();
    }

    @Test
    void repeatingCancelOfARefundedOrderDoesNotRefundAgain() {
        UUID owner = UUID.randomUUID();
        Order order = order(owner, Order.Status.CONFIRMED);
        order.setWalletCaptureEntryId(UUID.randomUUID());
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(walletClient.refundCapture(any(), any(), any())).thenReturn(UUID.randomUUID());

        service.cancelOrder(order.getId(), owner, false, "corr");
        service.cancelOrder(order.getId(), owner, false, "corr");

        verify(walletClient, times(1)).refundCapture(order.getWalletCaptureEntryId(), order.getId() + ":refund", "corr");
        verify(outboxRepository, times(2)).save(any());
    }

    @Test
    void cancellingAPendingOrderReleasesTheHoldAndDoesNotRefund() {
        UUID owner = UUID.randomUUID();
        Order order = order(owner, Order.Status.PENDING);
        order.setWalletHoldId(UUID.randomUUID());
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        service.cancelOrder(order.getId(), owner, false, "corr");

        assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED);
        verify(walletClient).releaseHold(order.getWalletHoldId(), order.getId() + ":release", "corr");
        verify(walletClient, never()).refundCapture(any(), any(), any());
        ArgumentCaptor<OutboxEvent> outbox = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(outbox.capture());
        assertThat(outbox.getAllValues()).extracting(OutboxEvent::getEventType)
                .doesNotContain("RefundPostedEvent");
    }

    @Test
    void confirmedOrderWithAHoldAndNoCaptureCannotBeCancelled() {
        UUID owner = UUID.randomUUID();
        Order order = order(owner, Order.Status.CONFIRMED);
        order.setWalletHoldId(UUID.randomUUID());
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancelOrder(order.getId(), owner, false, "corr"))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("cannot be cancelled");

        assertThat(order.getStatus()).isEqualTo(Order.Status.CONFIRMED);
        verify(orderRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
        verify(walletClient, never()).refundCapture(any(), any(), any());
        verify(walletClient, never()).releaseHold(any(), any(), any());
    }

    @Test
    void aFailedRefundLeavesTheConfirmedOrderUncancelled() {
        UUID owner = UUID.randomUUID();
        Order order = order(owner, Order.Status.CONFIRMED);
        order.setWalletCaptureEntryId(UUID.randomUUID());
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(walletClient.refundCapture(any(), any(), any()))
                .thenThrow(new ServiceUnavailableException("Wallet refund failed"));

        assertThatThrownBy(() -> service.cancelOrder(order.getId(), owner, false, "corr"))
                .isInstanceOf(ServiceUnavailableException.class);

        assertThat(order.getStatus()).isEqualTo(Order.Status.CONFIRMED);
        verify(orderRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void cancellingAConfirmedOrderWithNoWalletIdsDoesNotCallTheWallet() {
        UUID owner = UUID.randomUUID();
        Order order = order(owner, Order.Status.CONFIRMED);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        OrderResponse response = service.cancelOrder(order.getId(), owner, false, "corr");

        assertThat(response.getStatus()).isEqualTo("CANCELLED");
        verifyNoInteractions(walletClient);
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
        order.setShipRecipient("Ada Lovelace");
        order.setShipLine1("12 Enghelab");
        order.setShipCity("Tehran");
        order.setShipRegion("Tehran");
        order.setShipPostalCode("1136912345");
        order.setShipPhone("02112345678");
        order.setShipCountry("IR");

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
    void aTrackingReferenceCanStillBeRecordedAfterShippingWithoutOne() {
        // The ship endpoint accepts no body at all and the delivery UI labels the field optional, so an order
        // legitimately reaches SHIPPED with no reference. Nothing else in the system can write one afterwards.
        Order order = order(UUID.randomUUID(), Order.Status.SHIPPED);
        order.setTrackingReference(null);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        OrderResponse recorded = service.shipOrder(order.getId(), UUID.randomUUID(), "late-tracking", "TRACK-9");

        assertThat(recorded.getStatus()).isEqualTo("SHIPPED");
        assertThat(order.getTrackingReference()).isEqualTo("TRACK-9");
        verify(orderRepository).save(order);
        // Recording the reference is not a second shipment: no new fact and no new status transition.
        verify(outboxRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void recordingTheTrackingReferenceTwiceStaysIdempotent() {
        Order order = order(UUID.randomUUID(), Order.Status.SHIPPED);
        order.setTrackingReference(null);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        service.shipOrder(order.getId(), UUID.randomUUID(), "late-tracking", "TRACK-9");
        OrderResponse replay = service.shipOrder(order.getId(), UUID.randomUUID(), "late-tracking", "TRACK-9");

        assertThat(replay.getTrackingReference()).isEqualTo("TRACK-9");
        assertThatThrownBy(() -> service.shipOrder(
                order.getId(), UUID.randomUUID(), "late-tracking", "TRACK-OTHER"))
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

    @Test
    void checkoutWithoutAnAddressCreatesNoOrderAndNeverPlacesAHold() {
        UUID userId = UUID.randomUUID();
        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(UUID.randomUUID(), 1)));
        when(orderRepository.findByUserIdAndIdempotencyKeyWithItems(userId, "checkout-none"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createOrder(userId, request, "checkout-none", "corr"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(walletClient, never()).placeHold(any(), any(), anyLong(), any(), any());
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(storeClient);
    }

    @Test
    void checkoutWithAnotherCustomersAddressCreatesNoOrderAndNeverPlacesAHold() {
        UUID userId = UUID.randomUUID();
        CreateOrderRequest request = checkout(UUID.randomUUID(), 1);
        when(orderRepository.findByUserIdAndIdempotencyKeyWithItems(userId, "checkout-other"))
                .thenReturn(Optional.empty());
        when(addressBookClient.findOwned(userId, ADDRESS_ID))
                .thenThrow(new ResourceNotFoundException("Address not found"));

        assertThatThrownBy(() -> service.createOrder(userId, request, "checkout-other", "corr"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(walletClient, never()).placeHold(any(), any(), anyLong(), any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void aCountryOtherThanIrIsRejectedBeforeTheHold() {
        UUID userId = UUID.randomUUID();
        CreateOrderRequest request = checkout(UUID.randomUUID(), 1);
        when(orderRepository.findByUserIdAndIdempotencyKeyWithItems(userId, "checkout-country"))
                .thenReturn(Optional.empty());
        when(addressBookClient.findOwned(userId, ADDRESS_ID)).thenReturn(new AddressBookClient.ShipToAddress(
                "Ada", "12 Enghelab", null, "Tehran", "Tehran", "1136912345", "02112345678", "DE"));

        assertThatThrownBy(() -> service.createOrder(userId, request, "checkout-country", "corr"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(walletClient, never()).placeHold(any(), any(), anyLong(), any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void editingAndDeletingTheAddressBookLeavesTheOrderSnapshotAndShipReturnsIt() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(orderTransactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(orderRepository.findByUserIdAndIdempotencyKeyWithItems(userId, "checkout-snap"))
                .thenReturn(Optional.empty());
        when(storeClient.quote(any())).thenReturn(new StoreQuoteResponse(List.of(
                new StoreQuoteItemResponse(productId, "Lamp", new BigDecimal("10.00"), true, 1, 5, true))));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse created = service.createOrder(userId, checkout(productId, 1), "checkout-snap", "corr");

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        Order order = saved.getValue();
        assertThat(created.getShipRecipient()).isEqualTo("Ada Lovelace");
        assertThat(order.getShipLine1()).isEqualTo("12 Enghelab");

        clearInvocations(addressBookClient);
        order.setStatus(Order.Status.PACKAGED);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        OrderResponse shipped = service.shipOrder(order.getId(), UUID.randomUUID(), "ship-corr", "TRACK-1");

        assertThat(shipped.getShipRecipient()).isEqualTo("Ada Lovelace");
        assertThat(shipped.getShipLine1()).isEqualTo("12 Enghelab");
        assertThat(shipped.getShipLine2()).isEqualTo("Unit 4");
        assertThat(shipped.getShipCity()).isEqualTo("Tehran");
        assertThat(shipped.getShipRegion()).isEqualTo("Tehran");
        assertThat(shipped.getShipPostalCode()).isEqualTo("1136912345");
        assertThat(shipped.getShipPhone()).isEqualTo("02112345678");
        assertThat(shipped.getShipCountry()).isEqualTo("IR");
        assertThat(order.getShipRecipient()).isEqualTo("Ada Lovelace");
        verifyNoInteractions(addressBookClient);
    }

    private CreateOrderRequest checkout(UUID productId, int quantity) {
        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(productId, quantity)));
        request.setAddressId(ADDRESS_ID);
        return request;
    }

    private AddressBookClient.ShipToAddress shipTo() {
        return new AddressBookClient.ShipToAddress(
                "Ada Lovelace", "12 Enghelab", "Unit 4", "Tehran", "Tehran", "1136912345", "02112345678", "IR");
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

    private OrderItem orderItem(Order order, UUID productId, int quantity) {
        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setOrder(order);
        item.setProductId(productId);
        item.setProductName("Stored product");
        item.setUnitPrice(BigDecimal.TEN);
        item.setQuantity(quantity);
        item.setCreatedAt(Instant.now());
        item.setUpdatedAt(Instant.now());
        return item;
    }

    @Test
    void acceptsTheLongestCorrelationIdTheEdgeForwardsAndTruncatesItForStorage() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        CreateOrderRequest request = checkout(productId, 2);
        when(orderTransactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(orderRepository.findByUserIdAndIdempotencyKeyWithItems(userId, "checkout-long"))
                .thenReturn(Optional.empty());
        when(storeClient.quote(any())).thenReturn(new StoreQuoteResponse(List.of(
                new StoreQuoteItemResponse(productId, "Authoritative product", new BigDecimal("12.50"),
                        true, 2, 10, true))));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        // The gateway, security-starter, web-ui and kafka-common all accept [A-Za-z0-9._-]{1,128} and forward it
        // verbatim, so a 128-character id is a value this service is expected to receive.
        String edgeMaximum = "c".repeat(128);

        OrderResponse response = service.createOrder(userId, request, "checkout-long", edgeMaximum);

        assertThat(response).isNotNull();
        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getPayload()).contains("c".repeat(100));
    }

    @Test
    void aFailedStoreQuoteKeepsTheUpstreamCauseOnTheRejection() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        CreateOrderRequest request = checkout(productId, 2);
        when(orderRepository.findByUserIdAndIdempotencyKeyWithItems(userId, "checkout-down"))
                .thenReturn(Optional.empty());
        Request feignRequest = Request.create(Request.HttpMethod.POST, "/api/store/internal/products/quote",
                Map.of(), new byte[0], java.nio.charset.StandardCharsets.UTF_8, new RequestTemplate());
        FeignException upstream = new FeignException.ServiceUnavailable(
                "store-service is down", feignRequest, null, Map.of());
        when(storeClient.quote(any())).thenThrow(upstream);

        Throwable thrown = catchThrowable(() -> service.createOrder(userId, request, "checkout-down", "corr"));

        assertThat(thrown).isInstanceOf(ServiceUnavailableException.class);
        // Dropping the cause left nothing anywhere to say which dependency failed or how.
        assertThat(thrown).hasCause(upstream);
    }

    @Test
    void aShortBalanceDoesNotCreateAnOrder() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(orderRepository.findByUserIdAndIdempotencyKeyWithItems(userId, "checkout-short")).thenReturn(Optional.empty());
        when(storeClient.quote(any())).thenReturn(new StoreQuoteResponse(List.of(
                new StoreQuoteItemResponse(productId, "Product", new BigDecimal("12.50"), true, 1, 10, true))));
        when(walletClient.placeHold(any(), any(), anyLong(), any(), any()))
                .thenThrow(new InsufficientWalletBalanceException("INSUFFICIENT_FUNDS", "short"));

        assertThatThrownBy(() -> service.createOrder(userId, checkout(productId, 1), "checkout-short", "corr"))
                .isInstanceOf(InsufficientWalletBalanceException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void confirmCapturesTheHoldOnceAndARepeatDoesNotCaptureAgain() throws Exception {
        Order order = pendingWithHold();
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(processedRepository.insertIfAbsent(any(), anyString(), anyString(), anyInt(), anyLong())).thenReturn(1, 0);
        UUID captureId = UUID.randomUUID();
        when(walletClient.captureHold(eq(order.getWalletHoldId()), eq(order.getId() + ":capture"), any()))
                .thenReturn(captureId);
        order.setShipRecipient("Ada");
        order.setShipLine1("1 Main");
        order.setShipCity("Tehran");
        order.setShipRegion("Tehran");
        order.setShipPostalCode("12345");
        order.setShipPhone("09120000000");
        order.setShipCountry("IR");
        StockReservedEvent reserved = new StockReservedEvent();
        reserved.setOrderId(order.getId());
        reserved.setSuccess(true);

        service.processStockReserved(reserved, "stores.v2", 0, 1L);
        service.processStockReserved(reserved, "stores.v2", 0, 1L);

        assertThat(order.getStatus()).isEqualTo(Order.Status.CONFIRMED);
        assertThat(order.getWalletCaptureEntryId()).isEqualTo(captureId);
        verify(walletClient).captureHold(eq(order.getWalletHoldId()), eq(order.getId() + ":capture"), any());
        ArgumentCaptor<OutboxEvent> outbox = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(outbox.capture());
        OrderConfirmedEvent fact = new ObjectMapper().findAndRegisterModules()
                .readValue(outbox.getValue().getPayload(), OrderConfirmedEvent.class);
        assertThat(fact.getCapturedMinor()).isEqualTo(order.getWalletAmountMinor());
        assertThat(fact.getCapturedMinor()).isEqualTo(2500L);
        assertThat(fact.getWalletCaptureEntryId()).isEqualTo(captureId);
        assertThat(fact.getLines()).extracting(OrderConfirmedEvent.Line::getProductId)
                .containsExactly(order.getItems().get(0).getProductId());
    }

    @Test
    void insufficientStockReleasesTheHoldAndFailsTheOrder() {
        Order order = pendingWithHold();
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(processedRepository.insertIfAbsent(any(), anyString(), anyString(), anyInt(), anyLong())).thenReturn(1);
        StockInsufficientEvent event = new StockInsufficientEvent();
        event.setOrderId(order.getId());
        event.setReason("none left");

        service.processStockInsufficient(event, "stores.v2", 0, 2L);

        assertThat(order.getStatus()).isEqualTo(Order.Status.FAILED);
        verify(walletClient).releaseHold(eq(order.getWalletHoldId()), eq(order.getId() + ":release"), any());
        verify(walletClient, never()).captureHold(any(), any(), any());
    }

    private Order pendingWithHold() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setUserId(UUID.randomUUID());
        order.setStatus(Order.Status.PENDING);
        order.setWalletHoldId(UUID.randomUUID());
        order.setWalletAmountMinor(2500L);
        order.setItems(new java.util.ArrayList<>());
        OrderItem item = new OrderItem();
        item.setProductId(UUID.randomUUID());
        item.setQuantity(1);
        item.setOrder(order);
        order.getItems().add(item);
        return order;
    }

    @Test
    void anOrphanHoldOlderThanTheDeadlineIsReleasedAndAReplayDoesNotReleaseAgain() {
        UUID holdId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        WalletLedgerClient.OpenHold hold = new WalletLedgerClient.OpenHold(holdId, orderId, 2500, Instant.now().minusSeconds(3600));
        when(walletClient.listOpenHolds(any(), anyInt())).thenReturn(List.of(hold), List.of());
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThat(service.reconcileOpenWalletHolds(Instant.now(), 50)).isEqualTo(1);
        assertThat(service.reconcileOpenWalletHolds(Instant.now(), 50)).isZero();

        verify(walletClient).releaseHold(holdId, holdId + ":reconcile-release", "wallet-reconcile");
    }

    @Test
    void aConfirmedOrderWithAnOpenHoldIsCapturedOnce() {
        Order order = pendingWithHold();
        order.setStatus(Order.Status.CONFIRMED);
        UUID captureId = UUID.randomUUID();
        WalletLedgerClient.OpenHold hold = new WalletLedgerClient.OpenHold(
                order.getWalletHoldId(), order.getId(), 2500, Instant.now().minusSeconds(3600));
        when(walletClient.listOpenHolds(any(), anyInt())).thenReturn(List.of(hold));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(walletClient.captureHold(order.getWalletHoldId(), order.getId() + ":capture", "wallet-reconcile"))
                .thenReturn(captureId);

        assertThat(service.reconcileOpenWalletHolds(Instant.now(), 50)).isEqualTo(1);

        assertThat(order.getWalletCaptureEntryId()).isEqualTo(captureId);
        verify(walletClient).captureHold(order.getWalletHoldId(), order.getId() + ":capture", "wallet-reconcile");
        verify(walletClient, never()).releaseHold(any(), any(), any());
    }

    @Test
    void aReleasedHoldOnAConfirmedOrderIsLeftUnpaid() {
        Order order = pendingWithHold();
        order.setStatus(Order.Status.CONFIRMED);
        WalletLedgerClient.OpenHold hold = new WalletLedgerClient.OpenHold(
                order.getWalletHoldId(), order.getId(), 2500, Instant.now().minusSeconds(3600));
        when(walletClient.listOpenHolds(any(), anyInt())).thenReturn(List.of(hold));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(walletClient.captureHold(any(), any(), any())).thenThrow(new IllegalStateException("HOLD_NOT_OPEN"));

        assertThat(service.reconcileOpenWalletHolds(Instant.now(), 50)).isZero();
        assertThat(order.getStatus()).isEqualTo(Order.Status.CONFIRMED);
        assertThat(order.getWalletCaptureEntryId()).isNull();
    }

    @Test
    void aStalePendingOrderReleasesItsHold() {
        Order order = pendingWithHold();
        when(orderRepository.lockStalePendingOrders(any(), anyInt())).thenReturn(List.of(order));

        service.failStalePendingOrders(Instant.now(), 25);

        verify(walletClient).releaseHold(eq(order.getWalletHoldId()), eq(order.getId() + ":release"), any());
        assertThat(order.getStatus()).isEqualTo(Order.Status.FAILED);
    }
}
