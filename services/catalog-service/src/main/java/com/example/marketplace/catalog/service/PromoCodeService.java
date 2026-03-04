package com.example.marketplace.catalog.service;

import com.example.marketplace.catalog.auth.Role;
import com.example.marketplace.catalog.entity.DiscountType;
import com.example.marketplace.catalog.entity.PromoCodeEntity;
import com.example.marketplace.catalog.exception.AccessDeniedException;
import com.example.marketplace.catalog.model.PromoCodeCreate;
import com.example.marketplace.catalog.repository.PromoCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class PromoCodeService {

    private final PromoCodeRepository promoCodeRepository;

    public PromoCodeService(PromoCodeRepository promoCodeRepository) {
        this.promoCodeRepository = promoCodeRepository;
    }

    /**
     * Создание промокода. Только SELLER и ADMIN.
     */
    @Transactional
    public PromoCodeEntity create(Role role, PromoCodeCreate create) {
        if (role == Role.USER) {
            throw new AccessDeniedException("Роль USER не может создавать промокоды");
        }
        PromoCodeEntity entity = new PromoCodeEntity();
        entity.setCode(create.getCode().toUpperCase());
        entity.setDiscountType(toEntityDiscountType(create.getDiscountType()));
        entity.setDiscountValue(create.getDiscountValue());
        entity.setMinOrderAmount(create.getMinOrderAmount());
        entity.setMaxUses(create.getMaxUses());
        entity.setCurrentUses(0);
        entity.setValidFrom(create.getValidFrom().toInstant());
        entity.setValidUntil(create.getValidUntil().toInstant());
        entity.setActive(create.getActive() != null ? create.getActive() : true);
        return promoCodeRepository.save(entity);
    }

    private static DiscountType toEntityDiscountType(PromoCodeCreate.DiscountTypeEnum e) {
        return e == null ? DiscountType.PERCENTAGE : DiscountType.valueOf(e.name());
    }
}
