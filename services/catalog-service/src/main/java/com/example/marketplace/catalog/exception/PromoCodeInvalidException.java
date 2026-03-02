package com.example.marketplace.catalog.exception;

/**
 * Промокод не найден, истёк, исчерпан или неактивен (422 PROMO_CODE_INVALID).
 */
public class PromoCodeInvalidException extends RuntimeException {
    public PromoCodeInvalidException(String message) {
        super(message);
    }
}
