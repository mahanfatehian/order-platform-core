package com.orderprocessing.orderservice.repository;

import com.orderprocessing.orderservice.model.OrderReturn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderReturnRepository extends JpaRepository<OrderReturn, UUID> {
    Optional<OrderReturn> findByOrderId(UUID orderId);
}
