package com.orderprocessing.storeservice.repository;

import com.orderprocessing.storeservice.model.StockReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockReceiptRepository extends JpaRepository<StockReceipt, UUID> {
    Optional<StockReceipt> findByIdempotencyKey(String idempotencyKey);
}
