package com.orderprocessing.storeservice.repository;

import com.orderprocessing.storeservice.model.ProductPicture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductPictureRepository extends JpaRepository<ProductPicture, UUID> {
    boolean existsByProductId(UUID productId);

    long countByProductId(UUID productId);

    Optional<ProductPicture> findByIdAndProductId(UUID id, UUID productId);

    List<ProductPicture> findByProductIdInOrderByCreatedAtAsc(Collection<UUID> productIds);
}
