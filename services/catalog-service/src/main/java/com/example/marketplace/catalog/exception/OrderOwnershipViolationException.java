package com.example.marketplace.catalog.exception;

/**
 * Заказ принадлежит другому пользователю (403 ORDER_OWNERSHIP_VIOLATION).
 */
public class OrderOwnershipViolationException extends RuntimeException {
    public OrderOwnershipViolationException(String message) {
        super(message);
    }
}
