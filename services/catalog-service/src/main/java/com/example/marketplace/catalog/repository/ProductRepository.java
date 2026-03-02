package com.example.marketplace.catalog.repository;

import com.example.marketplace.catalog.entity.ProductEntity;
import com.example.marketplace.catalog.model.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {

    Optional<ProductEntity> findById(UUID id);

    Page<ProductEntity> findAll(Pageable pageable);

    Page<ProductEntity> findByStatus(ProductStatus status, Pageable pageable);

    Page<ProductEntity> findByCategory(String category, Pageable pageable);

    Page<ProductEntity> findByStatusAndCategory(ProductStatus status, String category, Pageable pageable);

    List<ProductEntity> findAllByStatus(ProductStatus status);

    List<ProductEntity> findAllByCategory(String category);

    List<ProductEntity> findAllByStatusAndCategory(ProductStatus status, String category);

    Optional<ProductEntity> findByIdAndSellerId(UUID id, UUID sellerId);
}
