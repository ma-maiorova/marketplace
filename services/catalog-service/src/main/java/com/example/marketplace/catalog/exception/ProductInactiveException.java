package com.example.marketplace.catalog.exception;

import java.util.UUID;

/**
 * Попытка заказать неактивный товар (409 PRODUCT_INACTIVE).
 */
public class ProductInactiveException extends RuntimeException {
    private final UUID productId;

    public ProductInactiveException(UUID productId) {
        super("Товар " + productId + " неактивен");
        this.productId = productId;
    }

    public UUID getProductId() {
        return productId;
    }
}
