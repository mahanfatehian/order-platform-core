package com.orderprocessing.storeservice.kafka;

import com.orderprocessing.kafkacommon.event.OrderCancelledEvent;
import com.orderprocessing.kafkacommon.event.OrderFailedEvent;
import com.orderprocessing.storeservice.service.InventoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class StoreKafkaConsumer {

    private final InventoryService inventoryService;

    public StoreKafkaConsumer(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @KafkaListener(topics = "order.events", groupId = "store-service")
    public void handleOrderCancelled(OrderCancelledEvent event) {
        log.info("Received OrderCancelled event for compensation: {}", event.getOrderId());
        if (event.getItems() != null && !event.getItems().isEmpty()) {
            inventoryService.releaseBatch(event.getItems());
            log.info("Successfully released inventory for cancelled order: {}", event.getOrderId());
        }
    }

    @KafkaListener(topics = "order.events", groupId = "store-service")
    public void handleOrderFailed(OrderFailedEvent event) {
        log.info("Received OrderFailed event for compensation: {}", event.getOrderId());
        if (event.getItems() != null && !event.getItems().isEmpty()) {
            inventoryService.releaseBatch(event.getItems());
            log.info("Successfully released inventory for failed order: {}", event.getOrderId());
        }
    }
}