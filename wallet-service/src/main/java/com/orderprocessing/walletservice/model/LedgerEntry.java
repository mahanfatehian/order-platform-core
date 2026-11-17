package com.orderprocessing.walletservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, updatable = false, length = 16)
    private EntryType entryType;

    @Column(name = "amount", nullable = false, updatable = false)
    private long amount;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "actor_id", nullable = false, updatable = false, length = 64)
    private String actorId;

    @Column(name = "actor_role", nullable = false, updatable = false, length = 32)
    private String actorRole;

    @Column(name = "reason", nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "correlation_id", nullable = false, updatable = false, length = 128)
    private String correlationId;

    @Column(name = "order_id", updatable = false)
    private UUID orderId;

    @Column(name = "hold_id", updatable = false)
    private UUID holdId;

    @Column(name = "related_entry_id", updatable = false)
    private UUID relatedEntryId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
