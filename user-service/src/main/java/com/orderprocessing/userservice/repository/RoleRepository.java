package com.orderprocessing.userservice.repository;

import com.orderprocessing.userservice.entity.RoleEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<RoleEntity, UUID> {

    @EntityGraph(attributePaths = {"authorities"})
    Optional<RoleEntity> findByName(String name);
}
