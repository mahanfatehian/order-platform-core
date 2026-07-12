package com.orderprocessing.storeservice.model;

import java.util.UUID;
import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @Column(name = "product_id", nullable = false, unique = true)
    private UUID productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;

    @Transient
    public int getAvailableQuantity() {
        return quantity - reservedQuantity;
    }
}
