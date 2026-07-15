package com.orderprocessing.orderservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit record for an order state transition.
 *
 * <p>{@link Immutable} prevents Hibernate from ever generating an update for a
 * history row. The service only exposes creation and read operations.</p>
 */
@Entity
@Immutable
@Table(name = "order_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "event_id", nullable = false, updatable = false, unique = true)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false)
    private Order.Status fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false)
    private Order.Status toStatus;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "actor_role", nullable = false, length = 64, updatable = false)
    private String actorRole;

    @Column(name = "reason", length = 500, updatable = false)
    private String reason;

    @Column(name = "correlation_id", nullable = false, length = 100, updatable = false)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    public static OrderStatusHistory record(UUID orderId,
                                            UUID eventId,
                                            Order.Status fromStatus,
                                            Order.Status toStatus,
                                            UUID actorUserId,
                                            String actorRole,
                                            String reason,
                                            String correlationId,
                                            Instant occurredAt) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.orderId = orderId;
        history.eventId = eventId;
        history.fromStatus = fromStatus;
        history.toStatus = toStatus;
        history.actorUserId = actorUserId;
        history.actorRole = actorRole;
        history.reason = reason;
        history.correlationId = correlationId;
        history.occurredAt = occurredAt;
        history.recordedAt = Instant.now();
        return history;
    }
}
