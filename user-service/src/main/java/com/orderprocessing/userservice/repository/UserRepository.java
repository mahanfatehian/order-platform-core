package com.orderprocessing.userservice.repository;

import com.orderprocessing.userservice.entity.UserEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    @EntityGraph(attributePaths = {"roles", "roles.authorities"})
    Optional<UserEntity> findById(UUID id);

    @EntityGraph(attributePaths = {"roles", "roles.authorities"})
    Optional<UserEntity> findByUsername(String username);

    @EntityGraph(attributePaths = {"roles", "roles.authorities"})
    Optional<UserEntity> findByEmail(String email);

    @EntityGraph(attributePaths = {"roles", "roles.authorities"})
    List<UserEntity> findAll();

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
