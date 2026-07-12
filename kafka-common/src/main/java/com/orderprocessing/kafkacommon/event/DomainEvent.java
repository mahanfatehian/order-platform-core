package com.orderprocessing.kafkacommon.event;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata shared by every event that crosses a Kafka boundary.
 *
 * <p>The event id is generated once, before an event is written to an outbox,
 * and remains stable across publisher retries. Consumers use it as their
 * durable idempotency key.</p>
 */
@Getter
@Setter
public abstract class DomainEvent {
    private UUID eventId = UUID.randomUUID();
    private Instant occurredAt = Instant.now();
    private int schemaVersion = 1;
    private String correlationId;
}
