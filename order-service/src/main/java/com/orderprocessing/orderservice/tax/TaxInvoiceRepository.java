package com.orderprocessing.orderservice.tax;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TaxInvoiceRepository extends JpaRepository<TaxInvoice, UUID> {
    Optional<TaxInvoice> findByOrderId(UUID orderId);
}
