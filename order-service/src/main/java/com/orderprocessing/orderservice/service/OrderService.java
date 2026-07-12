package com.orderprocessing.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.kafkacommon.KafkaEventRegistry;
import com.orderprocessing.kafkacommon.KafkaTopics;
import com.orderprocessing.kafkacommon.event.DomainEvent;
import com.orderprocessing.kafkacommon.event.OrderCancelledEvent;
import com.orderprocessing.kafkacommon.event.OrderPlacedEvent;
import com.orderprocessing.kafkacommon.event.StockInsufficientEvent;
import com.orderprocessing.kafkacommon.event.StockReservedEvent;
import com.orderprocessing.orderservice.client.StoreServiceClient;
import com.orderprocessing.orderservice.dto.CreateOrderRequest;
import com.orderprocessing.orderservice.dto.OrderItemRequest;
import com.orderprocessing.orderservice.dto.OrderItemResponse;
import com.orderprocessing.orderservice.dto.OrderResponse;
import com.orderprocessing.orderservice.dto.PageResponse;
import com.orderprocessing.orderservice.dto.StoreQuoteItemRequest;
import com.orderprocessing.orderservice.dto.StoreQuoteItemResponse;
import com.orderprocessing.orderservice.dto.StoreQuoteRequest;
import com.orderprocessing.orderservice.dto.StoreQuoteResponse;
import com.orderprocessing.orderservice.exception.ForbiddenOperationException;
import com.orderprocessing.orderservice.exception.InvalidOrderStateException;
import com.orderprocessing.orderservice.exception.ProductUnavailableException;
import com.orderprocessing.orderservice.exception.ResourceNotFoundException;
import com.orderprocessing.orderservice.exception.ServiceUnavailableException;
import com.orderprocessing.orderservice.model.Order;
import com.orderprocessing.orderservice.model.OrderItem;
import com.orderprocessing.orderservice.model.OutboxEvent;
import com.orderprocessing.orderservice.repository.OrderRepository;
import com.orderprocessing.orderservice.repository.OutboxEventRepository;
import com.orderprocessing.orderservice.repository.ProcessedKafkaEventRepository;
import feign.FeignException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProcessedKafkaEventRepository processedEventRepository;
    private final StoreServiceClient storeServiceClient;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository,
                        OutboxEventRepository outboxEventRepository,
                        ProcessedKafkaEventRepository processedEventRepository,
                        StoreServiceClient storeServiceClient,
                        ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.processedEventRepository = processedEventRepository;
        this.storeServiceClient = storeServiceClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OrderResponse createOrder(UUID userId, CreateOrderRequest request,
                                     String idempotencyKey, String correlationId) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        if (normalizedKey != null) {
            orderRepository.acquireIdempotencyLock(userId + ":" + normalizedKey);
            var existing = orderRepository.findByUserIdAndIdempotencyKeyWithItems(userId, normalizedKey);
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }

        Map<UUID, Integer> quantities = normalizeItems(request.getItems());
        StoreQuoteResponse quote = loadAuthoritativeQuote(quantities);
        Map<UUID, StoreQuoteItemResponse> quoteByProduct = validateQuote(quantities, quote);

        Instant now = Instant.now();
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setUserId(userId);
        order.setStatus(Order.Status.PENDING);
        order.setFailureReason(null);
        order.setIdempotencyKey(normalizedKey);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        List<OrderItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<UUID, Integer> requested : quantities.entrySet()) {
            StoreQuoteItemResponse product = quoteByProduct.get(requested.getKey());
            OrderItem item = new OrderItem();
            item.setId(UUID.randomUUID());
            item.setOrder(order);
            item.setProductId(product.productId());
            item.setProductName(product.name());
            item.setUnitPrice(product.unitPrice());
            item.setQuantity(requested.getValue());
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            items.add(item);
            total = total.add(product.unitPrice().multiply(BigDecimal.valueOf(requested.getValue())));
        }
        order.setItems(items);
        order.setTotalAmount(total);
        orderRepository.save(order);

        OrderPlacedEvent placed = new OrderPlacedEvent();
        placed.setOrderId(order.getId());
        placed.setUserId(userId);
        placed.setItems(quantities);
        placed.setCorrelationId(correlationId);
        addOutbox(order.getId(), KafkaTopics.ORDER_EVENTS, placed);
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getMyOrders(UUID userId, Order.Status status, Pageable pageable) {
        Specification<Order> specification = (root, query, cb) -> cb.equal(root.get("userId"), userId);
        if (status != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        Page<Order> orders = orderRepository.findAll(specification, pageable);
        return PageResponse.from(orders, this::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getAdminOrders(Order.Status status, UUID userId, UUID orderId, String search,
                                                       Pageable pageable) {
        Specification<Order> specification = Specification.where(null);
        if (status != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (userId != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("userId"), userId));
        }
        if (orderId != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("id"), orderId));
        }
        if (search != null && !search.isBlank()) {
            UUID searchId;
            try {
                searchId = UUID.fromString(search.trim());
            } catch (IllegalArgumentException ex) {
                searchId = null;
            }
            if (searchId == null) {
                specification = specification.and((root, query, cb) -> cb.disjunction());
            } else {
                UUID matchedId = searchId;
                specification = specification.and((root, query, cb) -> cb.or(
                        cb.equal(root.get("id"), matchedId), cb.equal(root.get("userId"), matchedId)));
            }
        }
        Page<Order> orders = orderRepository.findAll(specification, pageable);
        return PageResponse.from(orders, this::toResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID id, UUID actorUserId, boolean admin) {
        Order order = findWithItems(id);
        assertOwner(order, actorUserId, admin);
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getAdminOrder(UUID id) {
        return toResponse(findWithItems(id));
    }

    @Transactional
    public OrderResponse cancelOrder(UUID id, UUID actorUserId, boolean admin, String correlationId) {
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        assertOwner(order, actorUserId, admin);
        order.getItems().size();
        if (order.getStatus() == Order.Status.CANCELLED) {
            return toResponse(order);
        }
        if (order.getStatus() != Order.Status.PENDING && order.getStatus() != Order.Status.CONFIRMED) {
            throw new InvalidOrderStateException("Order cannot be cancelled in its current state");
        }

        order.setStatus(Order.Status.CANCELLED);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);

        OrderCancelledEvent cancelled = new OrderCancelledEvent();
        cancelled.setOrderId(order.getId());
        cancelled.setReason(admin ? "Order cancelled by administrator" : "Order cancelled by customer");
        cancelled.setCorrelationId(correlationId);
        addOutbox(order.getId(), KafkaTopics.ORDER_EVENTS, cancelled);
        return toResponse(order);
    }

    @Transactional
    public void processStockReserved(StockReservedEvent event, String topic, int partition, long offset) {
        if (!markProcessed(event, topic, partition, offset)) {
            return;
        }
        if (event.getOrderId() == null || !event.isSuccess()) {
            throw new IllegalArgumentException("Invalid StockReserved event");
        }
        Order order = lockOrder(event.getOrderId());
        if (order.getStatus() == Order.Status.PENDING) {
            order.setStatus(Order.Status.CONFIRMED);
            order.setFailureReason(null);
            order.setUpdatedAt(Instant.now());
        }
    }

    @Transactional
    public void processStockInsufficient(StockInsufficientEvent event, String topic, int partition, long offset) {
        if (!markProcessed(event, topic, partition, offset)) {
            return;
        }
        if (event.getOrderId() == null) {
            throw new IllegalArgumentException("Invalid StockInsufficient event");
        }
        Order order = lockOrder(event.getOrderId());
        if (order.getStatus() == Order.Status.PENDING) {
            order.setStatus(Order.Status.FAILED);
            order.setFailureReason(safeReason(event.getReason()));
            order.setUpdatedAt(Instant.now());
        }
    }

    private StoreQuoteResponse loadAuthoritativeQuote(Map<UUID, Integer> quantities) {
        StoreQuoteRequest request = new StoreQuoteRequest(quantities.entrySet().stream()
                .map(e -> new StoreQuoteItemRequest(e.getKey(), e.getValue())).toList());
        try {
            return storeServiceClient.quote(request);
        } catch (FeignException.NotFound ex) {
            throw new ResourceNotFoundException("One or more products were not found");
        } catch (FeignException ex) {
            throw new ServiceUnavailableException("Product pricing is temporarily unavailable");
        }
    }

    private Map<UUID, StoreQuoteItemResponse> validateQuote(Map<UUID, Integer> quantities,
                                                            StoreQuoteResponse response) {
        if (response == null || response.items() == null) {
            throw new ServiceUnavailableException("Store quote response was invalid");
        }
        Map<UUID, StoreQuoteItemResponse> byProduct = response.items().stream()
                .collect(Collectors.toMap(StoreQuoteItemResponse::productId, Function.identity(),
                        (left, right) -> left));
        if (byProduct.size() != quantities.size()) {
            throw new ServiceUnavailableException("Store quote response was incomplete");
        }
        for (Map.Entry<UUID, Integer> requested : quantities.entrySet()) {
            StoreQuoteItemResponse item = byProduct.get(requested.getKey());
            if (item == null || item.unitPrice() == null || item.unitPrice().signum() < 0
                    || item.requestedQuantity() != requested.getValue()) {
                throw new ServiceUnavailableException("Store quote response was invalid");
            }
            if (!item.active()) {
                throw new ProductUnavailableException("An inactive product cannot be ordered");
            }
        }
        return byProduct;
    }

    private Map<UUID, Integer> normalizeItems(List<OrderItemRequest> items) {
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        items.stream().sorted(Comparator.comparing(OrderItemRequest::getProductId)).forEach(item -> {
            int combined = Math.addExact(quantities.getOrDefault(item.getProductId(), 0), item.getQuantity());
            if (combined > 10000) {
                throw new IllegalArgumentException("Combined product quantity cannot exceed 10000");
            }
            quantities.put(item.getProductId(), combined);
        });
        return quantities;
    }

    private void addOutbox(UUID orderId, String topic, DomainEvent event) {
        OutboxEvent outbox = new OutboxEvent();
        outbox.setId(event.getEventId());
        outbox.setAggregateType("Order");
        outbox.setAggregateId(orderId.toString());
        outbox.setTopic(topic);
        outbox.setEventType(KafkaEventRegistry.eventType(event));
        try {
            outbox.setPayload(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize order event", ex);
        }
        outbox.setCreatedAt(Instant.now());
        outbox.setPublished(false);
        outbox.setAttemptCount(0);
        outbox.setDeadLettered(false);
        outboxEventRepository.save(outbox);
    }

    private boolean markProcessed(DomainEvent event, String topic, int partition, long offset) {
        UUID eventId = event.getEventId();
        if (eventId == null) {
            eventId = UUID.nameUUIDFromBytes((topic + ':' + partition + ':' + offset)
                    .getBytes(StandardCharsets.UTF_8));
            event.setEventId(eventId);
        }
        return processedEventRepository.insertIfAbsent(eventId, event.getClass().getSimpleName(), topic,
                partition, offset) == 1;
    }

    private Order lockOrder(UUID id) {
        return orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    private Order findWithItems(UUID id) {
        return orderRepository.findOrderWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    private void assertOwner(Order order, UUID actorUserId, boolean admin) {
        if (!admin && !order.getUserId().equals(actorUserId)) {
            throw new ForbiddenOperationException("This order belongs to another user");
        }
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String key = value.trim();
        if (key.length() > 100) {
            throw new IllegalArgumentException("Idempotency key cannot exceed 100 characters");
        }
        return key;
    }

    private String safeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Inventory is unavailable";
        }
        return reason.length() <= 500 ? reason : reason.substring(0, 500);
    }

    public OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream().map(item ->
                OrderItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .unitPrice(item.getUnitPrice())
                        .quantity(item.getQuantity())
                        .lineTotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                        .build()).toList();
        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .items(items)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .failureReason(order.getFailureReason())
                .cancellable(order.getStatus() == Order.Status.PENDING || order.getStatus() == Order.Status.CONFIRMED)
                .build();
    }
}
