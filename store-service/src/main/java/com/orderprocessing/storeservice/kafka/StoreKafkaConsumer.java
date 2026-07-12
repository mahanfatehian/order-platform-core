package com.orderprocessing.storeservice.kafka;

import com.orderprocessing.kafkacommon.KafkaTopics;
import com.orderprocessing.kafkacommon.event.OrderCancelledEvent;
import com.orderprocessing.kafkacommon.event.OrderPlacedEvent;
import com.orderprocessing.storeservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoreKafkaConsumer {
    private final InventoryService inventoryService;

    @KafkaListener(topics = KafkaTopics.ORDER_EVENTS, groupId = "store-service")
    public void handleOrderEvent(ConsumerRecord<String, Object> record) {
        Object event = record.value();
        if (event instanceof OrderPlacedEvent placed) {
            inventoryService.processOrderPlaced(placed, record.topic(), record.partition(), record.offset());
            return;
        }
        if (event instanceof OrderCancelledEvent cancelled) {
            inventoryService.processOrderCancelled(cancelled, record.topic(), record.partition(), record.offset());
            return;
        }
        log.debug("Ignoring order event type not consumed by store-service: {}",
                event == null ? "null" : event.getClass().getSimpleName());
    }
}
