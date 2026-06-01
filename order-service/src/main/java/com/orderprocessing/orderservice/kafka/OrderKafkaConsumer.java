package com.orderprocessing.orderservice.kafka;

import com.orderprocessing.kafkacommon.event.StockInsufficientEvent;
import com.orderprocessing.kafkacommon.event.StockReservedEvent;
import com.orderprocessing.kafkacommon.event.OrderConfirmedEvent;
import com.orderprocessing.kafkacommon.event.OrderFailedEvent;
import com.orderprocessing.orderservice.model.Order;
import com.orderprocessing.orderservice.model.OrderItem;
import com.orderprocessing.orderservice.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class OrderKafkaConsumer {

    private final OrderService orderService;

    public OrderKafkaConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @KafkaListener(topics = "store.events", groupId = "order-service")
    public void handleStockReserved(StockReservedEvent event) {
        log.info("Received StockReserved event: {}", event);
        
        try {
            // Update order status to CONFIRMED
            Order order = orderService.getOrderById(event.getOrderId());
            
            // Only update if order is still PENDING
            if (order.getStatus() == Order.Status.PENDING) {
                order.setStatus(Order.Status.CONFIRMED);
                order.setUpdatedAt(Instant.now());
                orderService.saveOrder(order);
                
                log.info("Order confirmed: {}", event.getOrderId());
                // In a real system, we would send an OrderConfirmed event here
            } else {
                log.warn("Received StockReserved event for order {} with status {}. Skipping update.", 
                        event.getOrderId(), order.getStatus());
            }
        } catch (Exception e) {
            log.error("Error handling StockReserved event for order {}: {}", 
                    event.getOrderId(), e.getMessage());
        }
    }

    @KafkaListener(topics = "store.events", groupId = "order-service")
    public void handleStockInsufficient(StockInsufficientEvent event) {
        log.info("Received StockInsufficient event: {}", event);
        
        try {
            // Update order status to FAILED
            Order order = orderService.getOrderById(event.getOrderId());
            
            // Only update if order is still PENDING
            if (order.getStatus() == Order.Status.PENDING) {
                order.setStatus(Order.Status.FAILED);
                order.setUpdatedAt(Instant.now());
                orderService.saveOrder(order);
                
                log.info("Order failed: {}", event.getOrderId());
                // In a real system, we would send an OrderFailed event here
            } else {
                log.warn("Received StockInsufficient event for order {} with status {}. Skipping update.", 
                        event.getOrderId(), order.getStatus());
            }
        } catch (Exception e) {
            log.error("Error handling StockInsufficient event for order {}: {}", 
                    event.getOrderId(), e.getMessage());
        }
    }
}