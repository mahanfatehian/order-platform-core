package com.orderprocessing.storeservice.repository;

import com.orderprocessing.storeservice.model.InventoryOrderLifecycle;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface InventoryOrderLifecycleRepository extends JpaRepository<InventoryOrderLifecycle, UUID> {

    @Modifying
    @Query(value = "insert into inventory_order_lifecycle (order_id, state, last_event_id, updated_at) "
            + "values (:orderId, :state, :eventId, :updatedAt) on conflict (order_id) do nothing", nativeQuery = true)
    int insertIfAbsent(@Param("orderId") UUID orderId, @Param("state") String state,
                       @Param("eventId") UUID eventId, @Param("updatedAt") Instant updatedAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lifecycle from InventoryOrderLifecycle lifecycle where lifecycle.orderId = :orderId")
    Optional<InventoryOrderLifecycle> findByOrderIdForUpdate(@Param("orderId") UUID orderId);
}
