package com.orderprocessing.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.kafkacommon.event.OrderCancelledEvent;
import com.orderprocessing.kafkacommon.event.OrderPlacedEvent;
import com.orderprocessing.orderservice.client.StoreServiceClient;
import com.orderprocessing.orderservice.model.Order;
import com.orderprocessing.orderservice.model.OrderItem;
import com.orderprocessing.orderservice.model.OutboxEvent;
import com.orderprocessing.orderservice.repository.OrderRepository;
import com.orderprocessing.orderservice.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final StoreServiceClient storeServiceClient;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository,
                        OutboxEventRepository outboxEventRepository,
                        StoreServiceClient storeServiceClient,
                        ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.storeServiceClient = storeServiceClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Order createOrder(UUID userId, List<OrderItem> items) {
        // 1. Build the order entity
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setUserId(userId);
        order.setStatus(Order.Status.PENDING);

        BigDecimal totalAmount = items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(totalAmount);

        Instant now = Instant.now();
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        // 2. Prepare the reservation map
        Map<UUID, Integer> reservations = items.stream()
                .collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getQuantity));

        // 3. Synchronous inventory reservation
        try {
            storeServiceClient.reserveBatch(reservations);
        } catch (Exception e) {
            log.error("Failed to reserve inventory for order: {}", e.getMessage());
            throw new RuntimeException("Inventory reservation failed: " + e.getMessage());
        }

        // 4. Save the order (PENDING)
        Order savedOrder = orderRepository.save(order);

        // 5. Publish OrderPlacedEvent atomically via Outbox
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setId(UUID.randomUUID());
        outboxEvent.setAggregateType("Order");
        outboxEvent.setAggregateId(savedOrder.getId().toString());
        outboxEvent.setEventType("OrderPlacedEvent");

        OrderPlacedEvent event = new OrderPlacedEvent();
        event.setOrderId(savedOrder.getId());
        event.setUserId(userId);
        event.setItems(reservations);

        try {
            outboxEvent.setPayload(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize OrderPlacedEvent", e);
        }

        outboxEvent.setCreatedAt(now);
        outboxEvent.setPublished(false);
        outboxEventRepository.save(outboxEvent);

        return savedOrder;
    }

    @Transactional
    public Order getOrderById(UUID id) {
        // Changed from findByIdWithItems to findOrderWithItemsById
        return orderRepository.findOrderWithItemsById(id)
                .orElseThrow(() -> new RuntimeException("Order not found with ID: " + id));
    }

    @Transactional
    public void cancelOrder(UUID id) {
        Order order = getOrderById(id);

        if (!canCancelOrder(order.getStatus())) {
            throw new RuntimeException("Order cannot be cancelled in its current state: " + order.getStatus());
        }

        // 1. Synchronously release inventory
        Map<UUID, Integer> itemsToRelease = order.getItems().stream()
                .collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getQuantity));

        try {
            storeServiceClient.releaseBatch(itemsToRelease);
        } catch (Exception e) {
            log.error("Failed to release inventory for cancelled order {}: {}", id, e.getMessage());
            throw new RuntimeException("Failed to release inventory: " + e.getMessage());
        }

        // 2. Update order status
        order.setStatus(Order.Status.CANCELLED);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);

        // 3. Publish OrderCancelledEvent via Outbox for downstream saga compensation
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setId(UUID.randomUUID());
        outboxEvent.setAggregateType("Order");
        outboxEvent.setAggregateId(order.getId().toString());
        outboxEvent.setEventType("OrderCancelledEvent");

        OrderCancelledEvent event = new OrderCancelledEvent();
        event.setOrderId(order.getId());
        event.setItems(itemsToRelease);
        event.setReason("Order cancelled by user");

        try {
            outboxEvent.setPayload(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize OrderCancelledEvent", e);
        }

        outboxEvent.setCreatedAt(Instant.now());
        outboxEvent.setPublished(false);
        outboxEventRepository.save(outboxEvent);
    }

    @Transactional
    public void saveOrder(Order order) {
        orderRepository.save(order);
    }

    private boolean canCancelOrder(Order.Status status) {
        return status == Order.Status.PENDING || status == Order.Status.CONFIRMED;
    }
}