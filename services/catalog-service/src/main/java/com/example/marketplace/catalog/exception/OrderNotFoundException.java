package com.example.marketplace.catalog.exception;

import java.util.UUID;

/**
 * Заказ не найден по ID (404 ORDER_NOT_FOUND).
 */
public class OrderNotFoundException extends RuntimeException {
    private final UUID orderId;

    public OrderNotFoundException(UUID orderId) {
        super("Заказ не найден: " + orderId);
        this.orderId = orderId;
    }

    public UUID getOrderId() {
        return orderId;
    }
}
