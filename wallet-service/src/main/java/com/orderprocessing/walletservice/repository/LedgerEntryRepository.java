package com.orderprocessing.walletservice.repository;

import com.orderprocessing.walletservice.model.EntryType;
import com.orderprocessing.walletservice.model.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    Optional<LedgerEntry> findByIdempotencyKey(String idempotencyKey);

    Optional<LedgerEntry> findByHoldIdAndEntryType(UUID holdId, EntryType entryType);

    Optional<LedgerEntry> findByRelatedEntryIdAndEntryType(UUID relatedEntryId, EntryType entryType);

    @Query("""
            select coalesce(sum(case
                when e.entryType in ('CREDIT', 'REFUND') then e.amount
                when e.entryType = 'CAPTURE' then -e.amount
                else 0
            end), 0)
            from LedgerEntry e
            where e.walletId = :walletId
            """)
    long booked(@Param("walletId") UUID walletId);

    List<LedgerEntry> findByWalletIdOrderByCreatedAtDesc(UUID walletId);

    long countByWalletId(UUID walletId);
}
