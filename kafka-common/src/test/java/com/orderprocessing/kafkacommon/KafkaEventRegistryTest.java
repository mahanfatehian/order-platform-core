package com.orderprocessing.kafkacommon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orderprocessing.kafkacommon.event.DomainEvent;
import com.orderprocessing.kafkacommon.event.OrderPlacedEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KafkaEventRegistryTest {
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void roundTripsAllowListedEventAndPreservesIdempotencyMetadata() throws Exception {
        OrderPlacedEvent source = new OrderPlacedEvent();
        source.setOrderId(UUID.randomUUID());
        source.setUserId(UUID.randomUUID());
        source.setItems(Map.of(UUID.randomUUID(), 2));
        source.setCorrelationId("correlation-123");

        String payload = objectMapper.writeValueAsString(source);
        DomainEvent decoded = KafkaEventRegistry.deserialize("OrderPlacedEvent", payload, objectMapper);

        assertThat(decoded).isInstanceOf(OrderPlacedEvent.class);
        assertThat(decoded.getEventId()).isEqualTo(source.getEventId());
        assertThat(decoded.getOccurredAt()).isEqualTo(source.getOccurredAt());
        assertThat(decoded.getCorrelationId()).isEqualTo("correlation-123");
        assertThat(((OrderPlacedEvent) decoded).getItems()).isEqualTo(source.getItems());
    }

    @Test
    void rejectsUnknownOutboxType() {
        assertThatThrownBy(() -> KafkaEventRegistry.deserialize("java.lang.Runtime", "{}", objectMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported Kafka event type");
    }
}
