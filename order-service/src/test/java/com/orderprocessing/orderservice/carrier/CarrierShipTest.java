package com.orderprocessing.orderservice.carrier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.kafkacommon.config.KafkaTopicNames;
import com.orderprocessing.orderservice.client.AddressBookClient;
import com.orderprocessing.orderservice.client.StoreServiceClient;
import com.orderprocessing.orderservice.client.WalletLedgerClient;
import com.orderprocessing.orderservice.exception.CarrierRejectedException;
import com.orderprocessing.orderservice.model.Order;
import com.orderprocessing.orderservice.repository.OrderRepository;
import com.orderprocessing.orderservice.repository.OrderStatusHistoryRepository;
import com.orderprocessing.orderservice.repository.OutboxEventRepository;
import com.orderprocessing.orderservice.repository.ProcessedKafkaEventRepository;
import com.orderprocessing.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CarrierShipTest {
    @Mock OrderRepository orderRepository;
    @Mock OutboxEventRepository outboxRepository;
    @Mock ProcessedKafkaEventRepository processedRepository;
    @Mock OrderStatusHistoryRepository historyRepository;
    @Mock StoreServiceClient storeClient;
    @Mock WalletLedgerClient walletClient;
    @Mock AddressBookClient addressBookClient;
    @Mock TransactionOperations orderTransactions;

    @Test
    void aRejectedReferenceDoesNotShip() {
        FakeCarrier carrier = new FakeCarrier();
        carrier.rejectNext();
        OrderService service = service(carrier);
        Order order = packaged();

        assertThatThrownBy(() -> service.shipOrder(order.getId(), UUID.randomUUID(), "corr", "TRACK-1"))
                .isInstanceOf(CarrierRejectedException.class);

        assertThat(order.getStatus()).isEqualTo(Order.Status.PACKAGED);
        verify(orderRepository, never()).findByIdForUpdate(any());
        verify(outboxRepository, never()).save(any());
    }

    private OrderService service(FakeCarrier carrier) {
        return new OrderService(orderRepository, outboxRepository, processedRepository, historyRepository,
                storeClient, walletClient, addressBookClient, new ObjectMapper().findAndRegisterModules(),
                orderTransactions, new KafkaTopicNames("orders.v2", "stores.v2"), null, carrier);
    }

    private static Order packaged() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setUserId(UUID.randomUUID());
        order.setStatus(Order.Status.PACKAGED);
        order.setTotalAmount(BigDecimal.TEN);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        order.setItems(new ArrayList<>());
        return order;
    }
}
