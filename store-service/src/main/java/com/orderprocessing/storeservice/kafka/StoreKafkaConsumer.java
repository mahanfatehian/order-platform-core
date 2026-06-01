package com.orderprocessing.storeservice.kafka;

import com.orderprocessing.kafkacommon.event.StockInsufficientEvent;
import com.orderprocessing.kafkacommon.event.StockReservedEvent;
import com.orderprocessing.kafkacommon.event.OrderPlacedEvent;
import com.orderprocessing.storeservice.service.InventoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class StoreKafkaConsumer {

    private final InventoryService inventoryService;
    private final StoreKafkaProducer kafkaProducer;

    public StoreKafkaConsumer(InventoryService inventoryService, StoreKafkaProducer kafkaProducer) {
        this.inventoryService = inventoryService;
        this.kafkaProducer = kafkaProducer;
    }

    @KafkaListener(topics = "order.events", groupId = "store-service")
    public void handleOrderPlaced(OrderPlacedEvent event) {
        log.info("Received OrderPlaced event: {}", event);
        
        try {
            // Process each item in the order
            event.getItems().forEach((productId, quantity) -> {
                try {
                    // Reserve inventory for this item
                    inventoryService.reserveInventory(productId, quantity);
                } catch (Exception e) {
                    log.error("Failed to reserve inventory for product {} in order {}: {}", 
                            productId, event.getOrderId(), e.getMessage());
                    // If reservation fails for any item, throw exception to trigger retry mechanism
                    throw new RuntimeException(String.format("Inventory reservation failed for product %s: %s", 
                            productId, e.getMessage()));
                }
            });
            
            // If all items were reserved successfully, send StockReserved event
            StockReservedEvent stockReservedEvent = new StockReservedEvent();
            stockReservedEvent.setOrderId(event.getOrderId());
            stockReservedEvent.setSuccess(true);
            
            kafkaProducer.sendStockReserved(stockReservedEvent);
            log.info("Successfully reserved inventory for order: {}", event.getOrderId());
            
        } catch (Exception e) {
            // If any part of the reservation failed, send StockInsufficient event
            StockInsufficientEvent stockInsufficientEvent = new StockInsufficientEvent();
            stockInsufficientEvent.setOrderId(event.getOrderId());
            stockInsufficientEvent.setSuccess(false);
            stockInsufficientEvent.setReason(e.getMessage());
            
            kafkaProducer.sendStockInsufficient(stockInsufficientEvent);
            log.error("Failed to reserve inventory for order: {}: {}", event.getOrderId(), e.getMessage());
        }
    }
}