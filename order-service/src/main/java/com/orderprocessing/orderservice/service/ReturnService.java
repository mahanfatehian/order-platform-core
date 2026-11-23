package com.orderprocessing.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.kafkacommon.KafkaEventRegistry;
import com.orderprocessing.kafkacommon.config.KafkaTopicNames;
import com.orderprocessing.kafkacommon.event.ReturnAcceptedEvent;
import com.orderprocessing.orderservice.client.WalletLedgerClient;
import com.orderprocessing.orderservice.dto.ReturnResponse;
import com.orderprocessing.orderservice.exception.ForbiddenOperationException;
import com.orderprocessing.orderservice.exception.ResourceNotFoundException;
import com.orderprocessing.orderservice.exception.ReturnNotAllowedException;
import com.orderprocessing.orderservice.model.Order;
import com.orderprocessing.orderservice.model.OrderItem;
import com.orderprocessing.orderservice.model.OutboxEvent;
import com.orderprocessing.orderservice.model.OrderReturn;
import com.orderprocessing.orderservice.model.ReturnStatus;
import com.orderprocessing.orderservice.repository.OrderRepository;
import com.orderprocessing.orderservice.repository.OutboxEventRepository;
import com.orderprocessing.orderservice.repository.OrderReturnRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class ReturnService {
    private final OrderRepository orders;
    private final OrderReturnRepository returns;
    private final WalletLedgerClient wallet;
    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;
    private final KafkaTopicNames topics;

    public ReturnService(OrderRepository orders, OrderReturnRepository returns, WalletLedgerClient wallet,
                         OutboxEventRepository outbox, ObjectMapper objectMapper, KafkaTopicNames topics) {
        this.orders = orders;
        this.returns = returns;
        this.wallet = wallet;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.topics = topics;
    }

    @Transactional
    public ReturnResponse request(UUID orderId, UUID actorUserId) {
        Order order = lock(orderId);
        if (!order.getUserId().equals(actorUserId)) {
            throw new ForbiddenOperationException("This order belongs to another user");
        }
        if (order.getStatus() != Order.Status.DELIVERED) {
            throw new ReturnNotAllowedException("Return requires a delivered order");
        }
        if (returns.findByOrderId(orderId).isPresent()) {
            throw new ReturnNotAllowedException("This order already has a return");
        }
        OrderReturn row = new OrderReturn();
        row.setId(UUID.randomUUID());
        row.setOrderId(orderId);
        row.setUserId(actorUserId);
        row.setStatus(ReturnStatus.REQUESTED);
        row.setRequestedAt(Instant.now());
        returns.save(row);
        return ReturnResponse.from(row, order.getStatus());
    }

    @Transactional
    public ReturnResponse reject(UUID orderId, UUID actorUserId) {
        Order order = lock(orderId);
        OrderReturn row = returns.findByOrderId(orderId)
                .orElseThrow(() -> new ReturnNotAllowedException("This order has no return request"));
        if (row.getStatus() == ReturnStatus.REJECTED) {
            return ReturnResponse.from(row, order.getStatus());
        }
        if (row.getStatus() != ReturnStatus.REQUESTED) {
            throw new ReturnNotAllowedException("Only a requested return can be rejected");
        }
        row.setStatus(ReturnStatus.REJECTED);
        row.setDecidedAt(Instant.now());
        row.setDecidedBy(actorUserId);
        returns.save(row);
        return ReturnResponse.from(row, order.getStatus());
    }

    @Transactional
    public ReturnResponse accept(UUID orderId, UUID actorUserId, String correlationId) {
        Order order = lock(orderId);
        OrderReturn row = returns.findByOrderId(orderId)
                .orElseThrow(() -> new ReturnNotAllowedException("This order has no return request"));
        if (row.getStatus() == ReturnStatus.ACCEPTED) {
            return ReturnResponse.from(row, order.getStatus());
        }
        if (row.getStatus() != ReturnStatus.REQUESTED || order.getStatus() != Order.Status.DELIVERED) {
            throw new ReturnNotAllowedException("Only a requested return of a delivered order can be accepted");
        }
        if (order.getWalletCaptureEntryId() != null) {
            UUID refundId = wallet.refundCapture(order.getWalletCaptureEntryId(),
                    order.getId() + ":return-refund", correlationId);
            row.setRefundEntryId(refundId);
        }
        row.setStatus(ReturnStatus.ACCEPTED);
        row.setDecidedAt(Instant.now());
        row.setDecidedBy(actorUserId);
        returns.save(row);
        publish(order, actorUserId, correlationId);
        return ReturnResponse.from(row, order.getStatus());
    }

    private void publish(Order order, UUID actorUserId, String correlationId) {
        ReturnAcceptedEvent event = new ReturnAcceptedEvent();
        event.setEventId(UUID.randomUUID());
        event.setOccurredAt(Instant.now());
        event.setSchemaVersion(1);
        event.setCorrelationId(correlationId);
        event.setOrderId(order.getId());
        event.setActorUserId(actorUserId);
        event.setItems(order.getItems().stream().collect(java.util.stream.Collectors.toMap(
                OrderItem::getProductId, OrderItem::getQuantity, Math::addExact, java.util.LinkedHashMap::new)));
        OutboxEvent row = new OutboxEvent();
        row.setId(event.getEventId());
        row.setAggregateType("Order");
        row.setAggregateId(order.getId().toString());
        row.setTopic(topics.orderEvents());
        row.setEventType(KafkaEventRegistry.eventType(event));
        try {
            row.setPayload(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize return event", ex);
        }
        row.setCreatedAt(Instant.now());
        row.setPublished(false);
        row.setAttemptCount(0);
        row.setDeadLettered(false);
        outbox.save(row);
    }

    private Order lock(UUID orderId) {
        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        order.getItems().size();
        return order;
    }
}
