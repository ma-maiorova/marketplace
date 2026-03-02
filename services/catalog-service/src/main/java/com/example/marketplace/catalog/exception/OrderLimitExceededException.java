package com.example.marketplace.catalog.exception;

/**
 * Превышен лимит частоты создания/обновления заказа (429 ORDER_LIMIT_EXCEEDED).
 */
public class OrderLimitExceededException extends RuntimeException {
    public OrderLimitExceededException(String message) {
        super(message);
    }
}
