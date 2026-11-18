package com.orderprocessing.userservice.repository;

import com.orderprocessing.userservice.entity.AddressEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<AddressEntity, UUID> {

    List<AddressEntity> findByUserIdOrderByDefaultAddressDescCreatedAtAsc(UUID userId);

    Optional<AddressEntity> findByIdAndUserId(UUID id, UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AddressEntity a set a.defaultAddress = false where a.userId = :userId and a.defaultAddress = true")
    int clearDefault(@Param("userId") UUID userId);
}
