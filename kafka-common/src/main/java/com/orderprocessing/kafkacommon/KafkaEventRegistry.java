package com.orderprocessing.kafkacommon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.kafkacommon.event.DomainEvent;
import com.orderprocessing.kafkacommon.event.OrderCancelledEvent;
import com.orderprocessing.kafkacommon.event.OrderConfirmedEvent;
import com.orderprocessing.kafkacommon.event.OrderFailedEvent;
import com.orderprocessing.kafkacommon.event.OrderPlacedEvent;
import com.orderprocessing.kafkacommon.event.StockInsufficientEvent;
import com.orderprocessing.kafkacommon.event.StockReservedEvent;

import java.util.Map;

/** Central allow-list used when converting transactional outbox JSON back to typed events. */
public final class KafkaEventRegistry {
    private static final Map<String, Class<? extends DomainEvent>> EVENT_TYPES = Map.of(
            OrderPlacedEvent.class.getSimpleName(), OrderPlacedEvent.class,
            OrderCancelledEvent.class.getSimpleName(), OrderCancelledEvent.class,
            OrderConfirmedEvent.class.getSimpleName(), OrderConfirmedEvent.class,
            OrderFailedEvent.class.getSimpleName(), OrderFailedEvent.class,
            StockReservedEvent.class.getSimpleName(), StockReservedEvent.class,
            StockInsufficientEvent.class.getSimpleName(), StockInsufficientEvent.class
    );

    private KafkaEventRegistry() {
    }

    public static String eventType(DomainEvent event) {
        String eventType = event.getClass().getSimpleName();
        if (!EVENT_TYPES.containsKey(eventType)) {
            throw new IllegalArgumentException("Unsupported Kafka event type: " + eventType);
        }
        return eventType;
    }

    public static DomainEvent deserialize(String eventType, String payload, ObjectMapper objectMapper)
            throws JsonProcessingException {
        Class<? extends DomainEvent> type = EVENT_TYPES.get(eventType);
        if (type == null) {
            throw new IllegalArgumentException("Unsupported Kafka event type: " + eventType);
        }
        return objectMapper.readValue(payload, type);
    }
}
