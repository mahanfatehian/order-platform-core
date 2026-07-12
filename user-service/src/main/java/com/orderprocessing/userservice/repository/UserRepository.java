package com.orderprocessing.userservice.repository;

import com.orderprocessing.userservice.entity.UserEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID>, JpaSpecificationExecutor<UserEntity> {

    @EntityGraph(attributePaths = {"roles"})
    Optional<UserEntity> findById(UUID id);

    @EntityGraph(attributePaths = {"roles"})
    Optional<UserEntity> findByUsernameIgnoreCase(String username);

    @EntityGraph(attributePaths = {"roles"})
    Optional<UserEntity> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = {"roles"})
    List<UserEntity> findAll();

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"roles"})
    @Query("select u from UserEntity u where u.id = :id")
    Optional<UserEntity> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"roles"})
    @Query(value = """
            select u from UserEntity u
            where (:search is null
                or lower(u.username) like lower(concat('%', :search, '%'))
                or lower(u.email) like lower(concat('%', :search, '%'))
                or lower(coalesce(u.firstName, '')) like lower(concat('%', :search, '%'))
                or lower(coalesce(u.lastName, '')) like lower(concat('%', :search, '%')))
              and (:enabled is null or u.enabled = :enabled)
            """,
            countQuery = """
            select count(u) from UserEntity u
            where (:search is null
                or lower(u.username) like lower(concat('%', :search, '%'))
                or lower(u.email) like lower(concat('%', :search, '%'))
                or lower(coalesce(u.firstName, '')) like lower(concat('%', :search, '%'))
                or lower(coalesce(u.lastName, '')) like lower(concat('%', :search, '%')))
              and (:enabled is null or u.enabled = :enabled)
            """)
    Page<UserEntity> search(@Param("search") String search, @Param("enabled") Boolean enabled, Pageable pageable);

    @Query("""
            select count(distinct u) from UserEntity u join u.roles r
            where r.name = 'ROLE_ADMIN' and u.enabled = true and u.accountNonLocked = true
            """)
    long countActiveAdmins();
}
