package com.orderprocessing.orderservice.tax;

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
@Table(name = "tax_invoices")
public class TaxInvoice {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "goods_minor", nullable = false, updatable = false)
    private long goodsMinor;

    @Column(name = "rate_bps", nullable = false, updatable = false)
    private int rateBps;

    @Column(name = "tax_minor", nullable = false, updatable = false)
    private long taxMinor;

    @Column(name = "charged_minor", nullable = false, updatable = false)
    private long chargedMinor;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;
}
