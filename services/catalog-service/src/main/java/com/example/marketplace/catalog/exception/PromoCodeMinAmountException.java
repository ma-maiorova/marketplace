package com.example.marketplace.catalog.exception;

/**
 * Сумма заказа ниже минимальной для промокода (422 PROMO_CODE_MIN_AMOUNT).
 */
public class PromoCodeMinAmountException extends RuntimeException {
    public PromoCodeMinAmountException(String message) {
        super(message);
    }
}
