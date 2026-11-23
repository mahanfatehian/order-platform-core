package com.orderprocessing.storeservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.kafkacommon.config.KafkaTopicNames;
import com.orderprocessing.kafkacommon.event.ReturnAcceptedEvent;
import com.orderprocessing.storeservice.model.Inventory;
import com.orderprocessing.storeservice.repository.InventoryOrderLifecycleRepository;
import com.orderprocessing.storeservice.repository.InventoryRepository;
import com.orderprocessing.storeservice.repository.InventoryReservationRepository;
import com.orderprocessing.storeservice.repository.ProcessedKafkaEventRepository;
import com.orderprocessing.storeservice.repository.ProductRepository;
import com.orderprocessing.storeservice.repository.StoreOutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReturnRestockTest {
    @Mock InventoryRepository inventoryRepository;
    @Mock InventoryOrderLifecycleRepository lifecycleRepository;
    @Mock InventoryReservationRepository reservationRepository;
    @Mock ProductRepository productRepository;
    @Mock ProcessedKafkaEventRepository processedRepository;
    @Mock StoreOutboxEventRepository outboxRepository;

    InventoryService service;

    @BeforeEach
    void setUp() {
        service = new InventoryService(inventoryRepository, lifecycleRepository, reservationRepository,
                productRepository, processedRepository, outboxRepository, new ObjectMapper().findAndRegisterModules(),
                new KafkaTopicNames("orders.v2", "stores.v2"));
    }

    @Test
    void acceptPutsTheSoldQuantityBackAndDoesNotTouchReserved() {
        UUID productId = UUID.randomUUID();
        Inventory stock = new Inventory();
        stock.setProductId(productId);
        stock.setQuantity(4);
        stock.setReservedQuantity(1);
        when(processedRepository.insertIfAbsent(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyLong())).thenReturn(1);
        when(inventoryRepository.findAllForUpdate(List.of(productId))).thenReturn(List.of(stock));
        ReturnAcceptedEvent event = new ReturnAcceptedEvent();
        event.setOrderId(UUID.randomUUID());
        event.setItems(Map.of(productId, 2));

        service.processReturnAccepted(event, "order.events", 0, 1L);

        assertThat(stock.getQuantity()).isEqualTo(6);
        assertThat(stock.getReservedQuantity()).isEqualTo(1);
    }
}
