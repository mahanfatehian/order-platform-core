package com.orderprocessing.kafkacommon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orderprocessing.kafkacommon.event.DomainEvent;
import com.orderprocessing.kafkacommon.event.OrderDeliveredEvent;
import com.orderprocessing.kafkacommon.event.OrderPackagedEvent;
import com.orderprocessing.kafkacommon.event.OrderPlacedEvent;
import com.orderprocessing.kafkacommon.event.OrderShippedEvent;
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

    @Test
    void roundTripsHumanLifecycleEvents() throws Exception {
        UUID orderId = UUID.randomUUID();

        OrderPackagedEvent packaged = new OrderPackagedEvent();
        packaged.setOrderId(orderId);
        packaged.setCorrelationId("package-correlation");
        OrderPackagedEvent decodedPackaged = (OrderPackagedEvent) roundTrip(packaged);
        assertThat(decodedPackaged.getOrderId()).isEqualTo(orderId);
        assertThat(decodedPackaged.getCorrelationId()).isEqualTo("package-correlation");

        OrderShippedEvent shipped = new OrderShippedEvent();
        shipped.setOrderId(orderId);
        UUID actorUserId = UUID.randomUUID();
        shipped.setActorUserId(actorUserId);
        shipped.setActorRole("ROLE_DELIVERY");
        shipped.setFromStatus("PACKAGED");
        shipped.setToStatus("SHIPPED");
        shipped.setTrackingReference("TRACK-123");
        shipped.setSchemaVersion(2);
        shipped.setCorrelationId("ship-correlation");
        OrderShippedEvent decodedShipped = (OrderShippedEvent) roundTrip(shipped);
        assertThat(decodedShipped.getOrderId()).isEqualTo(orderId);
        assertThat(decodedShipped.getCorrelationId()).isEqualTo("ship-correlation");
        assertThat(decodedShipped.getActorUserId()).isEqualTo(actorUserId);
        assertThat(decodedShipped.getActorRole()).isEqualTo("ROLE_DELIVERY");
        assertThat(decodedShipped.getFromStatus()).isEqualTo("PACKAGED");
        assertThat(decodedShipped.getToStatus()).isEqualTo("SHIPPED");
        assertThat(decodedShipped.getTrackingReference()).isEqualTo("TRACK-123");

        OrderDeliveredEvent delivered = new OrderDeliveredEvent();
        delivered.setOrderId(orderId);
        delivered.setCorrelationId("deliver-correlation");
        OrderDeliveredEvent decodedDelivered = (OrderDeliveredEvent) roundTrip(delivered);
        assertThat(decodedDelivered.getOrderId()).isEqualTo(orderId);
        assertThat(decodedDelivered.getCorrelationId()).isEqualTo("deliver-correlation");
    }

    @Test
    void deserializesLegacyFulfillmentPayloadWithoutNewActorMetadata() throws Exception {
        UUID orderId = UUID.randomUUID();
        String legacyPayload = "{\"orderId\":\"" + orderId + "\",\"schemaVersion\":1}";

        OrderPackagedEvent decoded = (OrderPackagedEvent) KafkaEventRegistry.deserialize(
                "OrderPackagedEvent", legacyPayload, objectMapper);

        assertThat(decoded.getOrderId()).isEqualTo(orderId);
        assertThat(decoded.getSchemaVersion()).isEqualTo(1);
        assertThat(decoded.getActorUserId()).isNull();
        assertThat(decoded.getActorRole()).isNull();
    }

    private DomainEvent roundTrip(DomainEvent source) throws Exception {
        String eventType = KafkaEventRegistry.eventType(source);
        String payload = objectMapper.writeValueAsString(source);
        DomainEvent decoded = KafkaEventRegistry.deserialize(eventType, payload, objectMapper);

        assertThat(eventType).isEqualTo(source.getClass().getSimpleName());
        assertThat(decoded).isInstanceOf(source.getClass());
        assertThat(decoded.getEventId()).isEqualTo(source.getEventId());
        assertThat(decoded.getOccurredAt()).isEqualTo(source.getOccurredAt());
        assertThat(decoded.getSchemaVersion()).isEqualTo(source.getSchemaVersion());
        return decoded;
    }
}
