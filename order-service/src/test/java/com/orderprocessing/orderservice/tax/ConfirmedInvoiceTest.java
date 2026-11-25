package com.orderprocessing.orderservice.tax;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.kafkacommon.config.KafkaTopicNames;
import com.orderprocessing.orderservice.client.AddressBookClient;
import com.orderprocessing.orderservice.client.StoreServiceClient;
import com.orderprocessing.orderservice.client.WalletLedgerClient;
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

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmedInvoiceTest {
    @Mock OrderRepository orderRepository;
    @Mock OutboxEventRepository outboxRepository;
    @Mock ProcessedKafkaEventRepository processedRepository;
    @Mock OrderStatusHistoryRepository historyRepository;
    @Mock StoreServiceClient storeClient;
    @Mock WalletLedgerClient walletClient;
    @Mock AddressBookClient addressBookClient;
    @Mock TransactionOperations orderTransactions;
    @Mock TaxInvoiceIssuer invoices;

    @Test
    void confirmIssuesOnceAndAFailedIssueDoesNotEscape() {
        OrderService service = new OrderService(orderRepository, outboxRepository, processedRepository, historyRepository,
                storeClient, walletClient, addressBookClient, new ObjectMapper().findAndRegisterModules(),
                orderTransactions, new KafkaTopicNames("orders.v2", "stores.v2"));
        service.setTaxInvoiceIssuer(invoices);
        Order order = new Order();
        order.setId(UUID.randomUUID());
        when(invoices.issueIfAbsent(order)).thenThrow(new IllegalStateException("table missing"));

        service.issueConfirmedInvoice(order);

        verify(invoices).issueIfAbsent(order);
    }
}
