package com.orderprocessing.storeservice.repository;

import com.orderprocessing.storeservice.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findByIdAndActiveTrue(UUID id);

    boolean existsBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCaseAndIdNot(String sku, UUID id);

    @Query(value = """
            select p from Product p
            where p.active = true
              and (:query = '' or lower(p.name) like lower(concat('%', :query, '%'))
                   or lower(coalesce(p.sku, '')) like lower(concat('%', :query, '%')))
              and (:inStock = false or exists (
                   select i.productId from Inventory i
                   where i.productId = p.id and i.quantity > i.reservedQuantity))
            """,
            countQuery = """
            select count(p) from Product p
            where p.active = true
              and (:query = '' or lower(p.name) like lower(concat('%', :query, '%'))
                   or lower(coalesce(p.sku, '')) like lower(concat('%', :query, '%')))
              and (:inStock = false or exists (
                   select i.productId from Inventory i
                   where i.productId = p.id and i.quantity > i.reservedQuantity))
            """)
    Page<Product> searchCatalog(@Param("query") String query,
                                @Param("inStock") boolean inStock,
                                Pageable pageable);

    @Query(value = """
            select p from Product p
            where :query = '' or lower(p.name) like lower(concat('%', :query, '%'))
                 or lower(coalesce(p.sku, '')) like lower(concat('%', :query, '%'))
            """,
            countQuery = """
            select count(p) from Product p
            where :query = '' or lower(p.name) like lower(concat('%', :query, '%'))
                 or lower(coalesce(p.sku, '')) like lower(concat('%', :query, '%'))
            """)
    Page<Product> searchAdmin(@Param("query") String query, Pageable pageable);

    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> findAllByIdOrdered(@Param("ids") Collection<UUID> ids);
}
