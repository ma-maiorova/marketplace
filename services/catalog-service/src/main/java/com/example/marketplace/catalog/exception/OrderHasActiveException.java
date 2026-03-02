package com.example.marketplace.catalog.exception;

/**
 * У пользователя уже есть активный заказ (409 ORDER_HAS_ACTIVE).
 */
public class OrderHasActiveException extends RuntimeException {
    public OrderHasActiveException(String message) {
        super(message);
    }
}
