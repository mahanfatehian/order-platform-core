package com.orderprocessing.storeservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "store_outbox_events")
@Getter
@Setter
public class StoreOutboxEvent {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;
    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;
    @Column(nullable = false)
    private String topic;
    @Column(name = "event_type", nullable = false)
    private String eventType;
    @Column(nullable = false, columnDefinition = "text")
    private String payload;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private boolean published;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "dead_lettered", nullable = false)
    private boolean deadLettered;
    @Column(name = "last_error")
    private String lastError;
}
