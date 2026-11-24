package com.orderprocessing.orderservice.tax;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.kafkacommon.config.KafkaTopicNames;
import com.orderprocessing.orderservice.client.AddressBookClient;
import com.orderprocessing.orderservice.client.StoreServiceClient;
import com.orderprocessing.orderservice.client.WalletLedgerClient;
import com.orderprocessing.orderservice.dto.CreateOrderRequest;
import com.orderprocessing.orderservice.dto.OrderItemRequest;
import com.orderprocessing.orderservice.dto.StoreQuoteItemResponse;
import com.orderprocessing.orderservice.dto.StoreQuoteResponse;
import com.orderprocessing.orderservice.model.Order;
import com.orderprocessing.orderservice.repository.OrderRepository;
import com.orderprocessing.orderservice.repository.OrderStatusHistoryRepository;
import com.orderprocessing.orderservice.repository.OutboxEventRepository;
import com.orderprocessing.orderservice.repository.ProcessedKafkaEventRepository;
import com.orderprocessing.orderservice.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxCheckoutTest {
    @Mock OrderRepository orderRepository;
    @Mock OutboxEventRepository outboxRepository;
    @Mock ProcessedKafkaEventRepository processedRepository;
    @Mock OrderStatusHistoryRepository historyRepository;
    @Mock StoreServiceClient storeClient;
    @Mock WalletLedgerClient walletClient;
    @Mock AddressBookClient addressBookClient;
    @Mock TransactionOperations orderTransactions;
    @Mock TaxRateService taxRates;

    OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, outboxRepository, processedRepository, historyRepository,
                storeClient, walletClient, addressBookClient, new ObjectMapper().findAndRegisterModules(),
                orderTransactions, new KafkaTopicNames("orders.v2", "stores.v2"), taxRates);
        when(taxRates.current()).thenReturn(new TaxTable(1000));
        when(walletClient.placeHold(any(), any(), anyLong(), any(), any()))
                .thenReturn(new WalletLedgerClient.HoldPlaced(UUID.randomUUID(), 1100));
        when(addressBookClient.findOwned(any(), any())).thenReturn(new AddressBookClient.ShipToAddress(
                "Ada Lovelace", "12 Enghelab", "Unit 4", "Tehran", "Tehran", "1136912345", "02112345678", "IR"));
        when(orderTransactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    void theHoldIncludesTaxAndTheOrderKeepsThatRate() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(orderRepository.findByUserIdAndIdempotencyKeyWithItems(userId, "tax-1")).thenReturn(Optional.empty());
        when(storeClient.quote(any())).thenReturn(new StoreQuoteResponse(List.of(
                new StoreQuoteItemResponse(productId, "Lamp", new BigDecimal("10.00"), true, 1, 5, true))));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(productId, 1)));
        request.setAddressId(UUID.randomUUID());

        service.createOrder(userId, request, "tax-1", "corr");

        verify(walletClient).placeHold(eq(userId), any(), eq(1100L), eq("tax-1"), eq("corr"));
        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        assertThat(saved.getValue().getTotalAmount()).isEqualByComparingTo("10.00");
        assertThat(saved.getValue().getTaxRateBps()).isEqualTo(1000);
        assertThat(saved.getValue().getTaxAmountMinor()).isEqualTo(100L);
    }
}
