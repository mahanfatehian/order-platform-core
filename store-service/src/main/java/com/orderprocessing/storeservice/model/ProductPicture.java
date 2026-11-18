package com.orderprocessing.storeservice.model;

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
@Table(name = "product_pictures")
public class ProductPicture {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "bytes", nullable = false)
    private byte[] bytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
