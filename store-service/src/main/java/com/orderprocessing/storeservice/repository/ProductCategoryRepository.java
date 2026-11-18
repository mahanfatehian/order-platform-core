package com.orderprocessing.storeservice.repository;

import com.orderprocessing.storeservice.model.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {
    Optional<ProductCategory> findByNameIgnoreCase(String name);

    List<ProductCategory> findAllByOrderByNameAsc();
}
