package com.example.marketplace.catalog.exception;

import java.util.UUID;

/**
 * Товар не найден по ID. Обрабатывается глобально → 404 с ApiError (PRODUCT_NOT_FOUND).
 */
public class ProductNotFoundException extends RuntimeException {

    private final UUID productId;

    public ProductNotFoundException(UUID productId) {
        super("Товар не найден: " + productId);
        this.productId = productId;
    }

    public UUID getProductId() {
        return productId;
    }
}
