package com.example.marketplace.catalog.repository;

import com.example.marketplace.catalog.entity.PromoCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PromoCodeRepository extends JpaRepository<PromoCodeEntity, UUID> {

    Optional<PromoCodeEntity> findByCode(String code);
}
