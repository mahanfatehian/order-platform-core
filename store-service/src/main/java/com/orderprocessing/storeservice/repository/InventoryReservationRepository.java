package com.orderprocessing.storeservice.repository;

import com.orderprocessing.storeservice.model.InventoryReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, UUID> {
    long countByStatus(InventoryReservation.Status status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from InventoryReservation r where r.orderId = :orderId order by r.productId")
    List<InventoryReservation> findByOrderIdForUpdate(@Param("orderId") UUID orderId);
}
