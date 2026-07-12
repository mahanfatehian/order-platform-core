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
@Table(name = "processed_kafka_events")
@Getter
@Setter
public class ProcessedKafkaEvent {
    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;
    @Column(name = "event_type", nullable = false)
    private String eventType;
    @Column(nullable = false)
    private String topic;
    @Column(name = "partition_number", nullable = false)
    private int partitionNumber;
    @Column(name = "record_offset", nullable = false)
    private long recordOffset;
    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
