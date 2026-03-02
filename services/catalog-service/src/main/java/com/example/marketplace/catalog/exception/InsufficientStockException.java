package com.example.marketplace.catalog.exception;

import java.util.List;

/**
 * Недостаточно товара на складе (409 INSUFFICIENT_STOCK).
 * details — список { product_id, requested_quantity, available_stock }.
 */
public class InsufficientStockException extends RuntimeException {
    private final List<StockShortageItem> items;

    public InsufficientStockException(List<StockShortageItem> items) {
        super("Недостаточно товара на складе");
        this.items = items;
    }

    public List<StockShortageItem> getItems() {
        return items;
    }

    public record StockShortageItem(java.util.UUID productId, int requestedQuantity, int availableStock) {}
}
