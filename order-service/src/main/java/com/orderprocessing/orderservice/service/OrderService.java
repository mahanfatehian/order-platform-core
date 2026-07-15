package com.orderprocessing.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.kafkacommon.KafkaEventRegistry;
import com.orderprocessing.kafkacommon.KafkaTopics;
import com.orderprocessing.kafkacommon.event.DomainEvent;
import com.orderprocessing.kafkacommon.event.OrderCancelledEvent;
import com.orderprocessing.kafkacommon.event.OrderDeliveredEvent;
import com.orderprocessing.kafkacommon.event.OrderFulfillmentEvent;
import com.orderprocessing.kafkacommon.event.OrderFailedEvent;
import com.orderprocessing.kafkacommon.event.OrderPackagedEvent;
import com.orderprocessing.kafkacommon.event.OrderPlacedEvent;
import com.orderprocessing.kafkacommon.event.OrderShippedEvent;
import com.orderprocessing.kafkacommon.event.StockInsufficientEvent;
import com.orderprocessing.kafkacommon.event.StockReservedEvent;
import com.orderprocessing.orderservice.client.StoreServiceClient;
import com.orderprocessing.orderservice.dto.CreateOrderRequest;
import com.orderprocessing.orderservice.dto.OrderItemRequest;
import com.orderprocessing.orderservice.dto.OrderItemResponse;
import com.orderprocessing.orderservice.dto.OrderResponse;
import com.orderprocessing.orderservice.dto.OrderStatusHistoryResponse;
import com.orderprocessing.orderservice.dto.PageResponse;
import com.orderprocessing.orderservice.dto.StoreQuoteItemRequest;
import com.orderprocessing.orderservice.dto.StoreQuoteItemResponse;
import com.orderprocessing.orderservice.dto.StoreQuoteRequest;
import com.orderprocessing.orderservice.dto.StoreQuoteResponse;
import com.orderprocessing.orderservice.exception.ForbiddenOperationException;
import com.orderprocessing.orderservice.exception.InvalidOrderStateException;
import com.orderprocessing.orderservice.exception.IdempotencyConflictException;
import com.orderprocessing.orderservice.exception.OrderTransitionConflictException;
import com.orderprocessing.orderservice.exception.ProductUnavailableException;
import com.orderprocessing.orderservice.exception.ResourceNotFoundException;
import com.orderprocessing.orderservice.exception.ServiceUnavailableException;
import com.orderprocessing.orderservice.model.Order;
import com.orderprocessing.orderservice.model.OrderItem;
import com.orderprocessing.orderservice.model.OrderStatusHistory;
import com.orderprocessing.orderservice.model.OutboxEvent;
import com.orderprocessing.orderservice.repository.OrderRepository;
import com.orderprocessing.orderservice.repository.OrderStatusHistoryRepository;
import com.orderprocessing.orderservice.repository.OutboxEventRepository;
import com.orderprocessing.orderservice.repository.ProcessedKafkaEventRepository;
import feign.FeignException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderService {
    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProcessedKafkaEventRepository processedEventRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final StoreServiceClient storeServiceClient;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository,
                        OutboxEventRepository outboxEventRepository,
                        ProcessedKafkaEventRepository processedEventRepository,
                        OrderStatusHistoryRepository historyRepository,
                        StoreServiceClient storeServiceClient,
                        ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.processedEventRepository = processedEventRepository;
        this.historyRepository = historyRepository;
        this.storeServiceClient = storeServiceClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OrderResponse createOrder(UUID userId, CreateOrderRequest request,
                                     String idempotencyKey, String correlationId) {
        String normalizedCorrelationId = normalizeCorrelationId(correlationId);
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
        placed.setCorrelationId(normalizedCorrelationId);
        recordHistory(order, placed, null, Order.Status.PENDING, userId, "ROLE_CUSTOMER",
                "Order submitted by customer");
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
    public PageResponse<OrderResponse> getFulfillmentOrders(Order.Status status,
                                                             Set<String> actorRoles,
                                                             Pageable pageable) {
        Set<Order.Status> allowedStatuses = new java.util.LinkedHashSet<>();
        if (actorRoles.contains("ROLE_WAREHOUSE")) {
            allowedStatuses.add(Order.Status.CONFIRMED);
        }
        if (actorRoles.contains("ROLE_DELIVERY")) {
            allowedStatuses.add(Order.Status.PACKAGED);
            allowedStatuses.add(Order.Status.SHIPPED);
        }
        if (allowedStatuses.isEmpty()) {
            throw new ForbiddenOperationException("A fulfillment role is required to view this queue");
        }
        if (status != null && !allowedStatuses.contains(status)) {
            throw new ForbiddenOperationException("Your role cannot view the " + status + " fulfillment queue");
        }

        Specification<Order> specification = status == null
                ? (root, query, cb) -> root.get("status").in(allowedStatuses)
                : (root, query, cb) -> cb.equal(root.get("status"), status);
        return PageResponse.from(orderRepository.findAll(specification, pageable), this::toResponse);
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

    @Transactional(readOnly = true)
    public List<OrderStatusHistoryResponse> getOrderHistory(UUID id,
                                                            UUID actorUserId,
                                                            boolean canViewAnyOrder) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        assertOwner(order, actorUserId, canViewAnyOrder);
        return historyRepository.findByOrderIdOrderByRecordedAtAsc(id).stream()
                .map(this::toHistoryResponse)
                .toList();
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

        Order.Status previousStatus = order.getStatus();
        String actorRole = admin ? "ROLE_ADMIN" : "ROLE_CUSTOMER";
        String reason = admin ? "Order cancelled by administrator" : "Order cancelled by customer";
        String normalizedCorrelationId = normalizeCorrelationId(correlationId);
        order.setStatus(Order.Status.CANCELLED);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);

        OrderCancelledEvent cancelled = new OrderCancelledEvent();
        cancelled.setOrderId(order.getId());
        cancelled.setItems(order.getItems().stream().collect(Collectors.toMap(
                OrderItem::getProductId, OrderItem::getQuantity, Math::addExact, LinkedHashMap::new)));
        cancelled.setReason(reason);
        cancelled.setActorUserId(actorUserId);
        cancelled.setActorRole(actorRole);
        cancelled.setFromStatus(previousStatus.name());
        cancelled.setToStatus(Order.Status.CANCELLED.name());
        cancelled.setSchemaVersion(2);
        cancelled.setCorrelationId(normalizedCorrelationId);
        recordHistory(order, cancelled, previousStatus, Order.Status.CANCELLED, actorUserId, actorRole, reason);
        addOutbox(order.getId(), KafkaTopics.ORDER_EVENTS, cancelled);
        return toResponse(order);
    }

    @Transactional
    public OrderResponse packOrder(UUID id, UUID actorUserId, String correlationId) {
        return applyFulfillmentCommand(id, actorUserId, "ROLE_WAREHOUSE", "pack",
                Order.Status.CONFIRMED, Order.Status.PACKAGED, correlationId, null,
                new OrderPackagedEvent(), "Order packaged by warehouse");
    }

    @Transactional
    public OrderResponse shipOrder(UUID id,
                                   UUID actorUserId,
                                   String correlationId,
                                   String trackingReference) {
        String normalizedTrackingReference = normalizeTrackingReference(trackingReference);
        return applyFulfillmentCommand(id, actorUserId, "ROLE_DELIVERY", "ship",
                Order.Status.PACKAGED, Order.Status.SHIPPED, correlationId, normalizedTrackingReference,
                new OrderShippedEvent(), normalizedTrackingReference == null
                        ? "Order handed to delivery"
                        : "Order handed to delivery with tracking reference " + normalizedTrackingReference);
    }

    @Transactional
    public OrderResponse deliverOrder(UUID id, UUID actorUserId, String correlationId) {
        return applyFulfillmentCommand(id, actorUserId, "ROLE_DELIVERY", "deliver",
                Order.Status.SHIPPED, Order.Status.DELIVERED, correlationId, null,
                new OrderDeliveredEvent(), "Order delivered to customer");
    }

    @Transactional
    public int failStalePendingOrders(Instant cutoff, int batchSize) {
        List<Order> staleOrders = orderRepository.lockStalePendingOrders(cutoff, batchSize);
        for (Order order : staleOrders) {
            order.getItems().size();
            Instant now = Instant.now();
            String reason = "Inventory confirmation timed out; reserved stock will be reconciled";
            order.setStatus(Order.Status.FAILED);
            order.setFailureReason(reason);
            order.setUpdatedAt(now);

            OrderFailedEvent failed = new OrderFailedEvent();
            failed.setOrderId(order.getId());
            failed.setItems(order.getItems().stream().collect(Collectors.toMap(
                    OrderItem::getProductId, OrderItem::getQuantity, Math::addExact, LinkedHashMap::new)));
            failed.setSuccess(false);
            failed.setReason(reason);
            failed.setSchemaVersion(2);
            failed.setOccurredAt(now);
            failed.setCorrelationId(UUID.randomUUID().toString());
            recordHistory(order, failed, Order.Status.PENDING, Order.Status.FAILED, null,
                    "SYSTEM_RECONCILIATION", reason);
            addOutbox(order.getId(), KafkaTopics.ORDER_EVENTS, failed);
        }
        return staleOrders.size();
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
            Instant occurredAt = Instant.now();
            order.setStatus(Order.Status.CONFIRMED);
            order.setFailureReason(null);
            order.setUpdatedAt(occurredAt);
            recordHistory(order, event, Order.Status.PENDING, Order.Status.CONFIRMED, null,
                    "SYSTEM_INVENTORY", "Inventory reservation confirmed");
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
            Instant occurredAt = Instant.now();
            order.setStatus(Order.Status.FAILED);
            order.setFailureReason(safeReason(event.getReason()));
            order.setUpdatedAt(occurredAt);
            recordHistory(order, event, Order.Status.PENDING, Order.Status.FAILED, null,
                    "SYSTEM_INVENTORY", order.getFailureReason());
        }
    }

    @Transactional
    public void processOrderPackaged(OrderPackagedEvent event, String topic, int partition, long offset) {
        observeFulfillmentFact(event, topic, partition, offset);
    }

    @Transactional
    public void processOrderShipped(OrderShippedEvent event, String topic, int partition, long offset) {
        observeFulfillmentFact(event, topic, partition, offset);
    }

    @Transactional
    public void processOrderDelivered(OrderDeliveredEvent event, String topic, int partition, long offset) {
        observeFulfillmentFact(event, topic, partition, offset);
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

    private OrderResponse applyFulfillmentCommand(UUID orderId,
                                                  UUID actorUserId,
                                                  String actorRole,
                                                  String action,
                                                  Order.Status expectedStatus,
                                                  Order.Status nextStatus,
                                                  String correlationId,
                                                  String trackingReference,
                                                  OrderFulfillmentEvent event,
                                                  String reason) {
        Order order = lockOrder(orderId);
        order.getItems().size();

        if (order.getStatus() == nextStatus) {
            if (nextStatus == Order.Status.SHIPPED && trackingReference != null
                    && !Objects.equals(order.getTrackingReference(), trackingReference)) {
                throw new IdempotencyConflictException(
                        "Order " + orderId + " was already shipped with a different tracking reference");
            }
            return toResponse(order);
        }
        if (order.getStatus() != expectedStatus) {
            throw new OrderTransitionConflictException(orderId, action, expectedStatus, order.getStatus());
        }

        String normalizedCorrelationId = normalizeCorrelationId(correlationId);
        Instant occurredAt = Instant.now();
        event.setEventId(UUID.randomUUID());
        event.setOccurredAt(occurredAt);
        event.setSchemaVersion(2);
        event.setCorrelationId(normalizedCorrelationId);
        event.setOrderId(orderId);
        event.setActorUserId(actorUserId);
        event.setActorRole(actorRole);
        event.setFromStatus(expectedStatus.name());
        event.setToStatus(nextStatus.name());
        event.setReason(reason);
        if (event instanceof OrderShippedEvent shipped) {
            shipped.setTrackingReference(trackingReference);
            order.setTrackingReference(trackingReference);
        }

        order.setStatus(nextStatus);
        order.setUpdatedAt(occurredAt);
        orderRepository.save(order);
        recordHistory(order, event, expectedStatus, nextStatus, actorUserId, actorRole, reason);
        addOutbox(orderId, KafkaTopics.ORDER_EVENTS, event);
        return toResponse(order);
    }

    /**
     * Fulfillment messages on {@code order.events} are facts emitted by this
     * service, never commands. Keeping this observer allows old version-one
     * payloads to deserialize and be acknowledged without granting Kafka
     * producers authority to change order state.
     */
    private void observeFulfillmentFact(OrderFulfillmentEvent event,
                                        String topic,
                                        int partition,
                                        long offset) {
        if (event.getOrderId() == null) {
            throw new IllegalArgumentException("Invalid fulfillment event: orderId is required");
        }
        if (markProcessed(event, topic, partition, offset)) {
            log.debug("Observed {} fact for order {}; authoritative state was already changed by its command",
                    event.getClass().getSimpleName(), event.getOrderId());
        }
    }

    private void recordHistory(Order order,
                               DomainEvent event,
                               Order.Status fromStatus,
                               Order.Status toStatus,
                               UUID actorUserId,
                               String actorRole,
                               String reason) {
        UUID eventId = event.getEventId();
        if (eventId == null) {
            eventId = UUID.randomUUID();
            event.setEventId(eventId);
        }
        Instant occurredAt = event.getOccurredAt();
        if (occurredAt == null) {
            occurredAt = Instant.now();
            event.setOccurredAt(occurredAt);
        }
        String correlationId = historyCorrelationId(event);
        historyRepository.save(OrderStatusHistory.record(order.getId(), eventId, fromStatus, toStatus,
                actorUserId, actorRole, safeHistoryReason(reason), correlationId, occurredAt));
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

    private String normalizeCorrelationId(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String correlationId = value.trim();
        if (correlationId.length() > 100) {
            throw new IllegalArgumentException("Correlation id cannot exceed 100 characters");
        }
        return correlationId;
    }

    private String historyCorrelationId(DomainEvent event) {
        String value = event.getCorrelationId();
        if (value == null || value.isBlank()) {
            value = UUID.randomUUID().toString();
        } else {
            value = value.trim();
            if (value.length() > 100) {
                value = value.substring(0, 100);
            }
        }
        event.setCorrelationId(value);
        return value;
    }

    private String normalizeTrackingReference(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trackingReference = value.trim();
        if (trackingReference.length() > 100) {
            throw new IllegalArgumentException("Tracking reference cannot exceed 100 characters");
        }
        return trackingReference;
    }

    private String safeHistoryReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return reason.length() <= 500 ? reason : reason.substring(0, 500);
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
                .trackingReference(order.getTrackingReference())
                .cancellable(order.getStatus() == Order.Status.PENDING || order.getStatus() == Order.Status.CONFIRMED)
                .build();
    }

    private OrderStatusHistoryResponse toHistoryResponse(OrderStatusHistory history) {
        return OrderStatusHistoryResponse.builder()
                .id(history.getId())
                .orderId(history.getOrderId())
                .eventId(history.getEventId())
                .fromStatus(history.getFromStatus() == null ? null : history.getFromStatus().name())
                .toStatus(history.getToStatus().name())
                .actorUserId(history.getActorUserId())
                .actorRole(history.getActorRole())
                .reason(history.getReason())
                .correlationId(history.getCorrelationId())
                .occurredAt(history.getOccurredAt())
                .recordedAt(history.getRecordedAt())
                .build();
    }
}
