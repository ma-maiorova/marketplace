package com.example.marketplace.catalog.repository;

import com.example.marketplace.catalog.entity.OrderEntity;
import com.example.marketplace.catalog.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    Optional<OrderEntity> findById(UUID id);

    List<OrderEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    boolean existsByUserIdAndStatusIn(UUID userId, List<OrderStatus> statuses);
}
