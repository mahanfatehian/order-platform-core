package com.orderprocessing.walletservice.repository;

import com.orderprocessing.walletservice.model.Hold;
import com.orderprocessing.walletservice.model.HoldStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HoldRepository extends JpaRepository<Hold, UUID> {

    Optional<Hold> findByIdempotencyKey(String idempotencyKey);

    @Query("select coalesce(sum(h.amount), 0) from Hold h where h.walletId = :walletId and h.status = :status")
    long sumAmountByWalletAndStatus(@Param("walletId") UUID walletId, @Param("status") HoldStatus status);

    List<Hold> findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(HoldStatus status, Instant createdBefore, Pageable pageable);
}
