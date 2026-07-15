package com.orderprocessing.storeservice.kafka;

import com.orderprocessing.kafkacommon.event.OrderDeliveredEvent;
import com.orderprocessing.kafkacommon.event.OrderFailedEvent;
import com.orderprocessing.storeservice.service.InventoryService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StoreKafkaConsumerTest {

    @Test
    void routesDeliveredEventsToInventorySettlement() {
        InventoryService inventoryService = mock(InventoryService.class);
        StoreKafkaConsumer consumer = new StoreKafkaConsumer(inventoryService);
        OrderDeliveredEvent event = new OrderDeliveredEvent();
        event.setOrderId(UUID.randomUUID());
        ConsumerRecord<String, Object> record = new ConsumerRecord<>("order.events", 2, 37L,
                event.getOrderId().toString(), event);

        consumer.handleOrderEvent(record);

        verify(inventoryService).processOrderDelivered(event, "order.events", 2, 37L);
    }

    @Test
    void routesFailedEventsToReservationCompensation() {
        InventoryService inventoryService = mock(InventoryService.class);
        StoreKafkaConsumer consumer = new StoreKafkaConsumer(inventoryService);
        OrderFailedEvent event = new OrderFailedEvent();
        event.setOrderId(UUID.randomUUID());
        ConsumerRecord<String, Object> record = new ConsumerRecord<>("order.events", 1, 41L,
                event.getOrderId().toString(), event);

        consumer.handleOrderEvent(record);

        verify(inventoryService).processOrderFailed(event, "order.events", 1, 41L);
    }
}
