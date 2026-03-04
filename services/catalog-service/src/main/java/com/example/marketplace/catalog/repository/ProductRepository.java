package com.example.marketplace.catalog.repository;

import com.example.marketplace.catalog.entity.ProductEntity;
import com.example.marketplace.catalog.model.ProductStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {

    Optional<ProductEntity> findById(UUID id);

    /**
     * Загрузка товаров с блокировкой FOR UPDATE для конкурентного резервирования остатков.
     * Id передаются отсортированными для единого порядка блокировки (избежание deadlock).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductEntity p WHERE p.id IN :ids ORDER BY p.id")
    List<ProductEntity> findAllByIdForUpdate(@Param("ids") List<UUID> ids);

    Page<ProductEntity> findAll(Pageable pageable);

    Page<ProductEntity> findByStatus(ProductStatus status, Pageable pageable);

    Page<ProductEntity> findByCategory(String category, Pageable pageable);

    Page<ProductEntity> findByStatusAndCategory(ProductStatus status, String category, Pageable pageable);

    List<ProductEntity> findAllByStatus(ProductStatus status);

    List<ProductEntity> findAllByCategory(String category);

    List<ProductEntity> findAllByStatusAndCategory(ProductStatus status, String category);

    Optional<ProductEntity> findByIdAndSellerId(UUID id, UUID sellerId);
}
