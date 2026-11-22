package com.orderprocessing.orderservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.kafkacommon.config.KafkaTopicNames;
import com.orderprocessing.orderservice.client.AddressBookClient;
import com.orderprocessing.orderservice.client.StoreServiceClient;
import com.orderprocessing.orderservice.client.WalletLedgerClient;
import com.orderprocessing.orderservice.exception.BlankTrackingReferenceException;
import com.orderprocessing.orderservice.exception.IdempotencyConflictException;
import com.orderprocessing.orderservice.exception.MissingShipAddressException;
import com.orderprocessing.orderservice.model.Order;
import com.orderprocessing.orderservice.repository.OrderRepository;
import com.orderprocessing.orderservice.repository.OrderStatusHistoryRepository;
import com.orderprocessing.orderservice.repository.OutboxEventRepository;
import com.orderprocessing.orderservice.repository.ProcessedKafkaEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionOperations;

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
class ShipDestinationTest {
    @Mock OrderRepository orderRepository;
    @Mock OutboxEventRepository outboxRepository;
    @Mock ProcessedKafkaEventRepository processedRepository;
    @Mock OrderStatusHistoryRepository historyRepository;
    @Mock StoreServiceClient storeClient;
    @Mock WalletLedgerClient walletClient;
    @Mock AddressBookClient addressBookClient;
    @Mock TransactionOperations orderTransactions;

    OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, outboxRepository, processedRepository, historyRepository,
                storeClient, walletClient, addressBookClient, new ObjectMapper().findAndRegisterModules(),
                orderTransactions, new KafkaTopicNames("orders.v2", "stores.v2"));
    }

    @Test
    void shipWithoutAnAddressStaysPackaged() {
        Order order = packaged();

        assertThatThrownBy(() -> service.shipOrder(order.getId(), UUID.randomUUID(), "corr", "TRACK-1"))
                .isInstanceOf(MissingShipAddressException.class);

        assertThat(order.getStatus()).isEqualTo(Order.Status.PACKAGED);
        verify(outboxRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void aBlankTrackingReferenceStaysPackaged() {
        Order order = packaged();
        stamp(order);

        assertThatThrownBy(() -> service.shipOrder(order.getId(), UUID.randomUUID(), "corr", "  "))
                .isInstanceOf(BlankTrackingReferenceException.class);

        assertThat(order.getStatus()).isEqualTo(Order.Status.PACKAGED);
        verify(outboxRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    private static void stamp(Order order) {
        order.setShipRecipient("Ada Lovelace");
        order.setShipLine1("12 Enghelab");
        order.setShipCity("Tehran");
        order.setShipRegion("Tehran");
        order.setShipPostalCode("1136912345");
        order.setShipPhone("02112345678");
        order.setShipCountry("IR");
    }

    private Order packaged() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setUserId(UUID.randomUUID());
        order.setStatus(Order.Status.PACKAGED);
        order.setTotalAmount(BigDecimal.TEN);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        order.setItems(new ArrayList<>());
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        return order;
    }
}
