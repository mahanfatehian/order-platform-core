package com.orderprocessing.orderservice.repository;

import com.orderprocessing.orderservice.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    // Spring Data JPA provides all basic CRUD operations automatically
    // Additional query methods can be defined here if needed
}