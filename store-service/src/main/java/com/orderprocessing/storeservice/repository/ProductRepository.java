package com.orderprocessing.storeservice.repository;

import com.orderprocessing.storeservice.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    // Spring Data JPA provides all basic CRUD operations automatically
    // Additional query methods can be defined here if needed
}