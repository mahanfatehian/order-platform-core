package com.orderprocessing.orderservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.kafkacommon.config.KafkaTopicNames;
import com.orderprocessing.kafkacommon.event.OrderCancelledEvent;
import com.orderprocessing.kafkacommon.event.StockInsufficientEvent;
import com.orderprocessing.kafkacommon.event.StockReservedEvent;
import com.orderprocessing.orderservice.client.StoreServiceClient;
import com.orderprocessing.orderservice.dto.OrderResponse;
import com.orderprocessing.orderservice.exception.ForbiddenOperationException;
import com.orderprocessing.orderservice.exception.InvalidOrderStateException;
import com.orderprocessing.orderservice.exception.OrderTransitionConflictException;
import com.orderprocessing.orderservice.model.Order;
import com.orderprocessing.orderservice.model.OrderItem;
import com.orderprocessing.orderservice.model.OrderStatusHistory;
import com.orderprocessing.orderservice.model.OutboxEvent;
import com.orderprocessing.orderservice.repository.OrderRepository;
import com.orderprocessing.orderservice.repository.OrderStatusHistoryRepository;
import com.orderprocessing.orderservice.repository.OutboxEventRepository;
import com.orderprocessing.orderservice.repository.ProcessedKafkaEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The order lifecycle as an executable table: every human command against every status, and every inventory
 * fact against every status. The edges are the ones the state diagram in docs/architecture.md draws, so a change
 * to either has to change both.
 */
class OrderLifecycleTransitionTest {
    private static final String ORDER_EVENTS = "orders.v2";

    enum Command { CANCEL, PACK, SHIP, DELIVER }

    private static final Map<Command, Map<Order.Status, Order.Status>> EDGES = Map.of(
            Command.CANCEL, Map.of(Order.Status.PENDING, Order.Status.CANCELLED,
                    Order.Status.CONFIRMED, Order.Status.CANCELLED),
            Command.PACK, Map.of(Order.Status.CONFIRMED, Order.Status.PACKAGED),
            Command.SHIP, Map.of(Order.Status.PACKAGED, Order.Status.SHIPPED),
            Command.DELIVER, Map.of(Order.Status.SHIPPED, Order.Status.DELIVERED));
    private static final Map<Command, Order.Status> TARGET = Map.of(
            Command.CANCEL, Order.Status.CANCELLED, Command.PACK, Order.Status.PACKAGED,
            Command.SHIP, Order.Status.SHIPPED, Command.DELIVER, Order.Status.DELIVERED);
    private static final Map<Command, String> FACT = Map.of(
            Command.CANCEL, "OrderCancelledEvent", Command.PACK, "OrderPackagedEvent",
            Command.SHIP, "OrderShippedEvent", Command.DELIVER, "OrderDeliveredEvent");
    private static final Map<Command, String> ROLE = Map.of(
            Command.CANCEL, "ROLE_CUSTOMER", Command.PACK, "ROLE_WAREHOUSE",
            Command.SHIP, "ROLE_DELIVERY", Command.DELIVER, "ROLE_DELIVERY");

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OutboxEventRepository outboxRepository = mock(OutboxEventRepository.class);
    private final ProcessedKafkaEventRepository processedRepository = mock(ProcessedKafkaEventRepository.class);
    private final OrderStatusHistoryRepository historyRepository = mock(OrderStatusHistoryRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final OrderService service = new OrderService(orderRepository, outboxRepository, processedRepository,
            historyRepository, mock(StoreServiceClient.class), objectMapper, mock(TransactionOperations.class),
            new KafkaTopicNames(ORDER_EVENTS, "stores.v2"));
    private final UUID owner = UUID.randomUUID();

    static Stream<Arguments> everyStatusAndCommand() {
        return Arrays.stream(Order.Status.values()).flatMap(status ->
                Arrays.stream(Command.values()).map(command -> Arguments.of(status, command)));
    }

    @ParameterizedTest(name = "{1} on a {0} order")
    @MethodSource("everyStatusAndCommand")
    void everyHumanCommandEitherFollowsADocumentedEdgeOrChangesNothing(Order.Status from, Command command) {
        Order order = stored(from);
        Order.Status edge = EDGES.get(command).get(from);

        if (edge != null) {
            OrderResponse response = apply(command, order);

            assertThat(order.getStatus()).isEqualTo(edge);
            assertThat(response.getStatus()).isEqualTo(edge.name());
            OutboxEvent fact = onlyOutboxRow();
            assertThat(fact.getTopic()).isEqualTo(ORDER_EVENTS);
            assertThat(fact.getEventType()).isEqualTo(FACT.get(command));
            OrderStatusHistory history = onlyHistoryRow();
            assertThat(history.getFromStatus()).isEqualTo(from);
            assertThat(history.getToStatus()).isEqualTo(edge);
            assertThat(history.getActorRole()).isEqualTo(ROLE.get(command));
            assertThat(history.getEventId()).isEqualTo(fact.getId());
        } else if (from == TARGET.get(command)) {
            assertThat(apply(command, order).getStatus()).isEqualTo(from.name());
            assertNothingWritten();
        } else {
            assertThatThrownBy(() -> apply(command, order)).isInstanceOf(command == Command.CANCEL
                    ? InvalidOrderStateException.class
                    : OrderTransitionConflictException.class);
            assertThat(order.getStatus()).isEqualTo(from);
            assertNothingWritten();
        }
    }

    @ParameterizedTest(name = "a reservation for a {0} order")
    @EnumSource(Order.Status.class)
    void aReservationConfirmsOnlyAPendingOrder(Order.Status from) {
        Order order = stored(from);
        order.setFailureReason("left over from an earlier attempt");
        everyFactIsNew();
        StockReservedEvent reserved = new StockReservedEvent();
        reserved.setOrderId(order.getId());
        reserved.setSuccess(true);

        service.processStockReserved(reserved, "stores.v2", 0, 1L);

        if (from == Order.Status.PENDING) {
            assertThat(order.getStatus()).isEqualTo(Order.Status.CONFIRMED);
            assertThat(order.getFailureReason()).isNull();
            OrderStatusHistory history = onlyHistoryRow();
            assertThat(history.getToStatus()).isEqualTo(Order.Status.CONFIRMED);
            assertThat(history.getActorRole()).isEqualTo("SYSTEM_INVENTORY");
            assertThat(history.getEventId()).isEqualTo(reserved.getEventId());
        } else {
            // A reservation arriving after a cancellation or a timeout must not bring the order back.
            assertThat(order.getStatus()).isEqualTo(from);
            verify(historyRepository, never()).save(any());
        }
        verify(outboxRepository, never()).save(any());
    }

    @ParameterizedTest(name = "a shortage for a {0} order")
    @EnumSource(Order.Status.class)
    void aStockShortageFailsOnlyAPendingOrder(Order.Status from) {
        Order order = stored(from);
        everyFactIsNew();

        service.processStockInsufficient(shortage(order, "Only 1 left in stock"), "stores.v2", 0, 2L);

        if (from == Order.Status.PENDING) {
            assertThat(order.getStatus()).isEqualTo(Order.Status.FAILED);
            assertThat(order.getFailureReason()).isEqualTo("Only 1 left in stock");
            assertThat(onlyHistoryRow().getToStatus()).isEqualTo(Order.Status.FAILED);
        } else {
            assertThat(order.getStatus()).isEqualTo(from);
            assertThat(order.getFailureReason()).isNull();
            verify(historyRepository, never()).save(any());
        }
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void aShortageWithoutAUsableReasonStillFailsTheOrderWithABoundedOne() {
        Order unexplained = stored(Order.Status.PENDING);
        Order verbose = stored(Order.Status.PENDING);
        everyFactIsNew();

        service.processStockInsufficient(shortage(unexplained, "  "), "stores.v2", 0, 3L);
        service.processStockInsufficient(shortage(verbose, "x".repeat(600)), "stores.v2", 0, 4L);

        assertThat(unexplained.getFailureReason()).isEqualTo("Inventory is unavailable");
        assertThat(verbose.getFailureReason()).hasSize(500);
    }

    @Test
    void aRedeliveredInventoryFactIsDroppedBeforeTheOrderIsLocked() {
        when(processedRepository.insertIfAbsent(any(), anyString(), anyString(), anyInt(), anyLong())).thenReturn(0);
        StockReservedEvent reserved = new StockReservedEvent();
        reserved.setOrderId(UUID.randomUUID());
        reserved.setSuccess(true);

        service.processStockReserved(reserved, "stores.v2", 0, 5L);

        verify(orderRepository, never()).findByIdForUpdate(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void anUnsuccessfulOrAnonymousReservationIsRejectedSoTheConsumerRetriesIt() {
        everyFactIsNew();
        StockReservedEvent unsuccessful = new StockReservedEvent();
        unsuccessful.setOrderId(UUID.randomUUID());
        StockReservedEvent anonymous = new StockReservedEvent();
        anonymous.setSuccess(true);

        assertThatThrownBy(() -> service.processStockReserved(unsuccessful, "stores.v2", 0, 6L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.processStockReserved(anonymous, "stores.v2", 0, 7L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(orderRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void cancellationEmitsACompensationFactCarryingEveryReservedQuantity() throws Exception {
        Order order = stored(Order.Status.CONFIRMED);
        UUID headphones = UUID.randomUUID();
        UUID hub = UUID.randomUUID();
        order.getItems().add(item(order, headphones, 2));
        order.getItems().add(item(order, hub, 3));

        service.cancelOrder(order.getId(), owner, false, "corr-cancel");

        OrderCancelledEvent fact = objectMapper.readValue(onlyOutboxRow().getPayload(), OrderCancelledEvent.class);
        assertThat(fact.getItems()).containsExactlyInAnyOrderEntriesOf(Map.of(headphones, 2, hub, 3));
        assertThat(fact.getFromStatus()).isEqualTo("CONFIRMED");
        assertThat(fact.getToStatus()).isEqualTo("CANCELLED");
        assertThat(fact.getActorUserId()).isEqualTo(owner);
        assertThat(fact.getActorRole()).isEqualTo("ROLE_CUSTOMER");
        assertThat(fact.getSchemaVersion()).isEqualTo(2);
        assertThat(fact.getCorrelationId()).isEqualTo("corr-cancel");
    }

    @Test
    void anAdministratorMayCancelAnotherCustomersOrderAndIsRecordedAsTheActor() {
        Order order = stored(Order.Status.PENDING);
        UUID administrator = UUID.randomUUID();

        service.cancelOrder(order.getId(), administrator, true, "corr-admin");

        assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED);
        OrderStatusHistory history = onlyHistoryRow();
        assertThat(history.getActorRole()).isEqualTo("ROLE_ADMIN");
        assertThat(history.getActorUserId()).isEqualTo(administrator);
    }

    @Test
    void aCustomerCannotCancelAnotherCustomersOrder() {
        Order order = stored(Order.Status.PENDING);

        assertThatThrownBy(() -> service.cancelOrder(order.getId(), UUID.randomUUID(), false, "corr"))
                .isInstanceOf(ForbiddenOperationException.class);

        assertThat(order.getStatus()).isEqualTo(Order.Status.PENDING);
        assertNothingWritten();
    }

    private OrderResponse apply(Command command, Order order) {
        return switch (command) {
            case CANCEL -> service.cancelOrder(order.getId(), owner, false, "corr-cancel");
            case PACK -> service.packOrder(order.getId(), UUID.randomUUID(), "corr-pack");
            case SHIP -> service.shipOrder(order.getId(), UUID.randomUUID(), "corr-ship", null);
            case DELIVER -> service.deliverOrder(order.getId(), UUID.randomUUID(), "corr-deliver");
        };
    }

    private Order stored(Order.Status status) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setUserId(owner);
        order.setStatus(status);
        order.setTotalAmount(BigDecimal.TEN);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        order.setItems(new ArrayList<>());
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        return order;
    }

    private static OrderItem item(Order order, UUID productId, int quantity) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProductId(productId);
        item.setProductName("Product " + productId);
        item.setUnitPrice(new BigDecimal("5.00"));
        item.setQuantity(quantity);
        return item;
    }

    private static StockInsufficientEvent shortage(Order order, String reason) {
        StockInsufficientEvent shortage = new StockInsufficientEvent();
        shortage.setOrderId(order.getId());
        shortage.setReason(reason);
        return shortage;
    }

    private void everyFactIsNew() {
        when(processedRepository.insertIfAbsent(any(), anyString(), anyString(), anyInt(), anyLong())).thenReturn(1);
    }

    private OutboxEvent onlyOutboxRow() {
        ArgumentCaptor<OutboxEvent> rows = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(rows.capture());
        return rows.getValue();
    }

    private OrderStatusHistory onlyHistoryRow() {
        ArgumentCaptor<OrderStatusHistory> rows = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(historyRepository).save(rows.capture());
        return rows.getValue();
    }

    private void assertNothingWritten() {
        verify(orderRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }
}
