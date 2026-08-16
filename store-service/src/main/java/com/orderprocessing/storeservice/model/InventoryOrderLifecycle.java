package com.orderprocessing.storeservice.model;

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

@Entity
@Table(name = "inventory_order_lifecycle")
@Getter
@Setter
public class InventoryOrderLifecycle {

    public enum State { ACTIVE, RELEASED, CONSUMED }

    @Id
    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state;

    @Column(name = "last_event_id", nullable = false)
    private UUID lastEventId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
