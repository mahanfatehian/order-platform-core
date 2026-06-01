package com.orderprocessing.orderservice.service;

import com.orderprocessing.orderservice.model.Order;
import com.orderprocessing.orderservice.model.OrderItem;
import com.orderprocessing.orderservice.repository.OrderRepository;
import com.orderprocessing.kafkacommon.event.OrderPlacedEvent; 
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;            
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;                                       
import java.util.Map;
import java.util.stream.Collectors;
import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderService(OrderRepository orderRepository, KafkaTemplate<String, Object> kafkaTemplate) {
        this.orderRepository = orderRepository;
        this.kafkaTemplate = kafkaTemplate;
    }
    @Transactional
    public Order createOrder(UUID userId, List<OrderItem> items) {
        // Create order
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setUserId(userId);
        order.setStatus(Order.Status.PENDING);
        
        // Calculate total amount using BigDecimal (matches numeric in DB)
        BigDecimal totalAmount = items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(totalAmount);
        
        // Set timestamps
        Instant now = Instant.now();
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        
        // Save order to database
        Order savedOrder = orderRepository.save(order);
        
        // Create and send OrderPlaced event
        OrderPlacedEvent event = new OrderPlacedEvent();
        event.setOrderId(savedOrder.getId());
        event.setUserId(userId);
        
        // Convert items to productId -> quantity map
        Map<UUID, Integer> itemMap = items.stream()
                .collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getQuantity));
        event.setItems(itemMap);
        
        kafkaTemplate.send("order.events", savedOrder.getId().toString(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to send OrderPlaced event {} to topic {}: {}", 
                            event, "order.events", ex.getMessage());
                } else {
                    log.info("Successfully sent OrderPlaced event {} to partition {} with offset {}", 
                            event, result.getRecordMetadata().partition(), 
                            result.getRecordMetadata().offset());
                }
            });
        
        return savedOrder;
    }

    @Transactional
    public Order getOrderById(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found with ID: " + id));
    }

    @Transactional
    public void cancelOrder(UUID id) {
        Order order = getOrderById(id);
        
        if (!canCancelOrder(order.getStatus())) {
            throw new RuntimeException("Order cannot be cancelled in its current state: " + order.getStatus());
        }
        
        // Update order status
        order.setStatus(Order.Status.CANCELLED);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        
        // In a real system, we would also send an OrderCancelled event to Kafka
    }


    @Transactional
    public void saveOrder(Order order) {
        orderRepository.save(order);
    }    

    private boolean canCancelOrder(Order.Status status) {
        return status == Order.Status.PENDING || status == Order.Status.CONFIRMED;
    }

}