package com.orderprocessing.orderservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.orderservice.client.StoreServiceClient;
import com.orderprocessing.orderservice.dto.CreateOrderRequest;
import com.orderprocessing.orderservice.dto.OrderItemRequest;
import com.orderprocessing.orderservice.dto.OrderResponse;
import com.orderprocessing.orderservice.dto.StoreQuoteItemResponse;
import com.orderprocessing.orderservice.dto.StoreQuoteResponse;
import com.orderprocessing.orderservice.exception.ForbiddenOperationException;
import com.orderprocessing.orderservice.model.Order;
import com.orderprocessing.orderservice.model.OutboxEvent;
import com.orderprocessing.orderservice.repository.OrderRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock private OrderRepository orderRepository;
    @Mock private OutboxEventRepository outboxRepository;
    @Mock private ProcessedKafkaEventRepository processedRepository;
    @Mock private StoreServiceClient storeClient;

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, outboxRepository, processedRepository,
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
    void repeatedCancellationOfAlreadyCancelledOrderDoesNotEmitAnotherEvent() {
        UUID owner = UUID.randomUUID();
        Order order = order(owner, Order.Status.CANCELLED);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        OrderResponse response = service.cancelOrder(order.getId(), owner, false, "corr");

        assertThat(response.getStatus()).isEqualTo("CANCELLED");
        verify(orderRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
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
