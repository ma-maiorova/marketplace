package com.example.marketplace.catalog.repository;

import com.example.marketplace.catalog.entity.PromoCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PromoCodeRepository extends JpaRepository<PromoCodeEntity, UUID> {

    Optional<PromoCodeEntity> findByCode(String code);

    /** Атомарный инкремент current_uses только если current_uses < max_uses. Возвращает число обновлённых строк (1 или 0). */
    @Modifying
    @Query("UPDATE PromoCodeEntity p SET p.currentUses = p.currentUses + 1 WHERE p.id = :id AND p.currentUses < p.maxUses")
    int incrementCurrentUsesIfUnderMax(@Param("id") UUID id);

    /** Атомарный декремент current_uses только если current_uses > 0 (защита от ухода в минус при гонках). */
    @Modifying
    @Query("UPDATE PromoCodeEntity p SET p.currentUses = p.currentUses - 1 WHERE p.id = :id AND p.currentUses > 0")
    int decrementCurrentUsesIfPositive(@Param("id") UUID id);
}
