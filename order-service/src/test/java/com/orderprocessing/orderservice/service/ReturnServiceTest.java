package com.orderprocessing.orderservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.kafkacommon.config.KafkaTopicNames;
import com.orderprocessing.orderservice.client.WalletLedgerClient;
import com.orderprocessing.orderservice.dto.ReturnResponse;
import com.orderprocessing.orderservice.exception.ReturnNotAllowedException;
import com.orderprocessing.orderservice.model.Order;
import com.orderprocessing.orderservice.model.OrderItem;
import com.orderprocessing.orderservice.model.OrderReturn;
import com.orderprocessing.orderservice.model.ReturnStatus;
import com.orderprocessing.orderservice.repository.OrderRepository;
import com.orderprocessing.orderservice.repository.OrderReturnRepository;
import com.orderprocessing.orderservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReturnServiceTest {
    @Mock OrderRepository orders;
    @Mock OrderReturnRepository returns;
    @Mock WalletLedgerClient wallet;
    @Mock OutboxEventRepository outbox;

    ReturnService service;
    UUID owner = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ReturnService(orders, returns, wallet, outbox,
                new ObjectMapper().findAndRegisterModules(), new KafkaTopicNames("orders.v2", "stores.v2"));
    }

    @Test
    void aDeliveredOrderCanBeRequestedAndStaysDelivered() {
        Order order = delivered();
        when(returns.findByOrderId(order.getId())).thenReturn(Optional.empty());
        when(returns.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReturnResponse response = service.request(order.getId(), owner);

        assertThat(response.status()).isEqualTo("REQUESTED");
        assertThat(response.orderStatus()).isEqualTo("DELIVERED");
        assertThat(order.getStatus()).isEqualTo(Order.Status.DELIVERED);
        verify(wallet, never()).refundCapture(any(), any(), any());
    }

    @Test
    void aShippedOrderCannotBeReturned() {
        Order order = delivered();
        order.setStatus(Order.Status.SHIPPED);

        assertThatThrownBy(() -> service.request(order.getId(), owner))
                .isInstanceOf(ReturnNotAllowedException.class);
        assertThat(order.getStatus()).isEqualTo(Order.Status.SHIPPED);
        verify(returns, never()).save(any());
    }

    @Test
    void aSecondRequestIsRejected() {
        Order order = delivered();
        when(returns.findByOrderId(order.getId())).thenReturn(Optional.of(new OrderReturn()));

        assertThatThrownBy(() -> service.request(order.getId(), owner))
                .isInstanceOf(ReturnNotAllowedException.class);
        verify(wallet, never()).refundCapture(any(), any(), any());
    }

    @Test
    void rejectLeavesMoneyWhereItIs() {
        Order order = delivered();
        OrderReturn row = requested(order);
        when(returns.findByOrderId(order.getId())).thenReturn(Optional.of(row));
        when(returns.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReturnResponse response = service.reject(order.getId(), UUID.randomUUID());

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(order.getStatus()).isEqualTo(Order.Status.DELIVERED);
        verify(wallet, never()).refundCapture(any(), any(), any());
        verify(outbox, never()).save(any());
    }

    private OrderReturn requested(Order order) {
        OrderReturn row = new OrderReturn();
        row.setId(UUID.randomUUID());
        row.setOrderId(order.getId());
        row.setStatus(ReturnStatus.REQUESTED);
        return row;
    }

    private Order delivered() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setUserId(owner);
        order.setStatus(Order.Status.DELIVERED);
        order.setTotalAmount(BigDecimal.TEN);
        order.setItems(new ArrayList<>());
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        return order;
    }
}
