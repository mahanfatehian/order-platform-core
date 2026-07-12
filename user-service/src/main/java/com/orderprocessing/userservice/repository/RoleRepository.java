package com.orderprocessing.userservice.repository;

import com.orderprocessing.userservice.entity.RoleEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<RoleEntity, UUID> {

    @EntityGraph(attributePaths = {"authorities"})
    Optional<RoleEntity> findByName(String name);

    List<RoleEntity> findAllByNameIn(Collection<String> names);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RoleEntity r where r.name = :name")
    Optional<RoleEntity> findByNameForUpdate(@Param("name") String name);
}
