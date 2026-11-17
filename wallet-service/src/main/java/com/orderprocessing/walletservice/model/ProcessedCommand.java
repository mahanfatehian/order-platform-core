package com.orderprocessing.walletservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "processed_commands")
public class ProcessedCommand {

    @Id
    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
    private String requestHash;

    @Column(name = "entry_id", nullable = false, updatable = false)
    private UUID entryId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
