package com.example.marketplace.catalog.repository;

import com.example.marketplace.catalog.entity.UserOperationEntity;
import com.example.marketplace.catalog.entity.UserOperationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserOperationRepository extends JpaRepository<UserOperationEntity, UUID> {

    Optional<UserOperationEntity> findFirstByUserIdAndOperationTypeOrderByCreatedAtDesc(UUID userId, UserOperationType type);
}
