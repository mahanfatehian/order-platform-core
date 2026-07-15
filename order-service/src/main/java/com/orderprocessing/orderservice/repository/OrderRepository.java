package com.orderprocessing.orderservice.repository;

import com.orderprocessing.orderservice.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import jakarta.persistence.LockModeType;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    long countByStatus(Order.Status status);


    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items")
    List<Order> findAllWithItems();

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.userId = :userId ORDER BY o.createdAt DESC")
    List<Order> findAllByUserIdWithItems(@Param("userId") UUID userId);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<Order> findOrderWithItemsById(@Param("id") UUID id);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.userId = :userId AND o.idempotencyKey = :key")
    Optional<Order> findByUserIdAndIdempotencyKeyWithItems(@Param("userId") UUID userId,
                                                            @Param("key") String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = """
            select * from orders
            where status = 'PENDING' and created_at < :cutoff
            order by created_at
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<Order> lockStalePendingOrders(@Param("cutoff") Instant cutoff,
                                       @Param("batchSize") int batchSize);

    @Query(value = """
            select 1
            from (select pg_advisory_xact_lock(hashtextextended(:lockKey, 0))) lock_result
            """, nativeQuery = true)
    Integer acquireIdempotencyLock(@Param("lockKey") String lockKey);

}
