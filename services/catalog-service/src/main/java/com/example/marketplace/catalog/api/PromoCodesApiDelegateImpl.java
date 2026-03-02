package com.example.marketplace.catalog.api;

import com.example.marketplace.catalog.auth.UserPrincipal;
import com.example.marketplace.catalog.entity.PromoCodeEntity;
import com.example.marketplace.catalog.model.PromoCodeCreate;
import com.example.marketplace.catalog.model.PromoCodeResponse;
import com.example.marketplace.catalog.service.PromoCodeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;

@Service
public class PromoCodesApiDelegateImpl implements PromoCodesApiDelegate {

    private final PromoCodeService promoCodeService;

    public PromoCodesApiDelegateImpl(PromoCodeService promoCodeService) {
        this.promoCodeService = promoCodeService;
    }

    @Override
    public ResponseEntity<PromoCodeResponse> createPromoCode(PromoCodeCreate promoCodeCreate) {
        UserPrincipal principal = getPrincipal();
        PromoCodeEntity entity = promoCodeService.create(principal.getRole(), promoCodeCreate);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(entity));
    }

    private static UserPrincipal getPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal p)) {
            throw new com.example.marketplace.catalog.exception.AccessDeniedException("Требуется аутентификация");
        }
        return p;
    }

    private static PromoCodeResponse toResponse(PromoCodeEntity e) {
        PromoCodeResponse r = new PromoCodeResponse();
        r.setId(e.getId());
        r.setCode(e.getCode());
        r.setDiscountType(PromoCodeResponse.DiscountTypeEnum.valueOf(e.getDiscountType().name()));
        r.setDiscountValue(e.getDiscountValue());
        r.setMinOrderAmount(e.getMinOrderAmount());
        r.setMaxUses(e.getMaxUses());
        r.setCurrentUses(e.getCurrentUses());
        r.setValidFrom(e.getValidFrom().atOffset(ZoneOffset.UTC));
        r.setValidUntil(e.getValidUntil().atOffset(ZoneOffset.UTC));
        r.setActive(e.getActive());
        return r;
    }
}
