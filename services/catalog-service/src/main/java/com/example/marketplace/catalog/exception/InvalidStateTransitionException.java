package com.example.marketplace.catalog.exception;

import com.example.marketplace.catalog.model.OrderStatus;

/**
 * Недопустимый переход состояния заказа (409 INVALID_STATE_TRANSITION).
 */
public class InvalidStateTransitionException extends RuntimeException {
    private final OrderStatus from;
    private final OrderStatus to;

    public InvalidStateTransitionException(OrderStatus from, OrderStatus to) {
        super("Недопустимый переход состояния заказа: из " + from + " в " + to);
        this.from = from;
        this.to = to;
    }

    public OrderStatus getFrom() {
        return from;
    }

    public OrderStatus getTo() {
        return to;
    }
}
