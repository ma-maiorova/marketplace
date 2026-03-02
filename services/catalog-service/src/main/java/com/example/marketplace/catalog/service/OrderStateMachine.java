package com.example.marketplace.catalog.service;

import com.example.marketplace.catalog.exception.InvalidStateTransitionException;
import com.example.marketplace.catalog.model.OrderStatus;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Модель допустимых переходов состояний заказа:
 * CREATED → PAYMENT_PENDING | CANCELED
 * PAYMENT_PENDING → PAID | CANCELED
 * PAID → SHIPPED
 * SHIPPED → COMPLETED
 * COMPLETED, CANCELED — конечные (переходы запрещены).
 */
public final class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.CREATED, EnumSet.of(OrderStatus.PAYMENT_PENDING, OrderStatus.CANCELED),
            OrderStatus.PAYMENT_PENDING, EnumSet.of(OrderStatus.PAID, OrderStatus.CANCELED),
            OrderStatus.PAID, EnumSet.of(OrderStatus.SHIPPED),
            OrderStatus.SHIPPED, EnumSet.of(OrderStatus.COMPLETED),
            OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class),
            OrderStatus.CANCELED, EnumSet.noneOf(OrderStatus.class)
    );

    private OrderStateMachine() {
    }

    /**
     * Проверяет, допустим ли переход из текущего статуса в новый.
     * При недопустимом переходе выбрасывает {@link InvalidStateTransitionException}.
     */
    public static void validateTransition(OrderStatus current, OrderStatus newStatus) {
        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.get(current);
        if (allowed == null || !allowed.contains(newStatus)) {
            throw new InvalidStateTransitionException(current, newStatus);
        }
    }

    /**
     * Возвращает true, если переход допустим (для проверок без исключения).
     */
    public static boolean isTransitionAllowed(OrderStatus current, OrderStatus newStatus) {
        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.get(current);
        return allowed != null && allowed.contains(newStatus);
    }
}
