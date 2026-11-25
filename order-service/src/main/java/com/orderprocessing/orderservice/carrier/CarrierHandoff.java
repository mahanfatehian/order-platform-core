package com.orderprocessing.orderservice.carrier;

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
@Table(name = "carrier_handoffs")
public class CarrierHandoff {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "tracking_reference", nullable = false, length = 100)
    private String trackingReference;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;
}
