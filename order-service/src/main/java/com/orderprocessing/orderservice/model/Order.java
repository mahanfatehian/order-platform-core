package com.orderprocessing.orderservice.model;

import java.util.UUID;
import java.time.Instant;
import java.util.List;
import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "orders")
@Getter
@Setter
public class Order {

    public enum Status {
        PENDING, CONFIRMED, PACKAGED, SHIPPED, DELIVERED, CANCELLED, FAILED
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "tracking_reference", length = 100)
    private String trackingReference;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Paged listings load orders through a {@code Specification}, which cannot
     * join-fetch without moving pagination into memory. Batching the lazy
     * collection keeps pagination in the database while resolving the items of
     * a whole page in a single {@code IN (...)} query instead of one per order.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100)
    private List<OrderItem> items = new java.util.ArrayList<>();
}
