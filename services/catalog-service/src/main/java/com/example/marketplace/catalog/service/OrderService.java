package com.example.marketplace.catalog.service;

import com.example.marketplace.catalog.auth.Role;
import com.example.marketplace.catalog.entity.*;
import com.example.marketplace.catalog.exception.*;
import com.example.marketplace.catalog.model.OrderCreate;
import com.example.marketplace.catalog.model.OrderItemCreate;
import com.example.marketplace.catalog.model.OrderStatus;
import com.example.marketplace.catalog.model.OrderUpdate;
import com.example.marketplace.catalog.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Создание заказа с последовательными проверками (1–7) и созданием в одной транзакции.
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final UserOperationRepository userOperationRepository;

    @Value("${order.creation.minutes-limit:5}")
    private int creationMinutesLimit;

    private static final OrderStatus CREATED = OrderStatus.CREATED;
    private static final OrderStatus PAYMENT_PENDING = OrderStatus.PAYMENT_PENDING;
    private static final BigDecimal MAX_DISCOUNT_PERCENT = new BigDecimal("70");

    public OrderService(OrderRepository orderRepository,
                        ProductRepository productRepository,
                        PromoCodeRepository promoCodeRepository,
                        UserOperationRepository userOperationRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.promoCodeRepository = promoCodeRepository;
        this.userOperationRepository = userOperationRepository;
    }

    /**
     * Создание заказа. SELLER — запрещено; USER и ADMIN — разрешено.
     */
    @Transactional
    public OrderEntity createOrder(UUID userId, Role role, OrderCreate orderCreate) {
        if (role == Role.SELLER) {
            throw new AccessDeniedException("Роль SELLER не может создавать заказы");
        }
        List<OrderItemCreate> items = orderCreate.getItems();
        String promoCodeStr = orderCreate.getPromoCode() != null && orderCreate.getPromoCode().isPresent()
                ? orderCreate.getPromoCode().get() : null;

        // 1. Ограничение частоты создания
        userOperationRepository
                .findFirstByUserIdAndOperationTypeOrderByCreatedAtDesc(userId, UserOperationType.CREATE_ORDER)
                .ifPresent(last -> {
                    if (last.getCreatedAt().plusSeconds(creationMinutesLimit * 60L).isAfter(Instant.now())) {
                        throw new OrderLimitExceededException("Превышен лимит частоты создания заказа. Подождите " + creationMinutesLimit + " мин.");
                    }
                });

        // 2. Проверка активных заказов
        if (orderRepository.existsByUserIdAndStatusIn(userId, List.of(CREATED, PAYMENT_PENDING))) {
            throw new OrderHasActiveException("У пользователя уже есть активный заказ");
        }

        // 3–5. Блокировка товаров (FOR UPDATE в одном порядке — избежание deadlock), проверка каталога и остатков, резерв
        List<UUID> productIds = items.stream().map(OrderItemCreate::getProductId).distinct().sorted().toList();
        List<ProductEntity> loaded = productRepository.findAllByIdForUpdate(productIds);
        Map<UUID, ProductEntity> productById = loaded.stream().collect(Collectors.toMap(ProductEntity::getId, p -> p));
        List<ProductEntity> products = new ArrayList<>();
        for (OrderItemCreate item : items) {
            ProductEntity p = productById.get(item.getProductId());
            if (p == null) {
                throw new ProductNotFoundException(item.getProductId());
            }
            products.add(p);
        }
        for (ProductEntity p : products) {
            if (p.getStatus() != com.example.marketplace.catalog.model.ProductStatus.ACTIVE) {
                throw new ProductInactiveException(p.getId());
            }
        }
        List<InsufficientStockException.StockShortageItem> shortage = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ProductEntity p = products.get(i);
            OrderItemCreate item = items.get(i);
            if (p.getStock() < item.getQuantity()) {
                shortage.add(new InsufficientStockException.StockShortageItem(
                        p.getId(), item.getQuantity(), p.getStock()));
            }
        }
        if (!shortage.isEmpty()) {
            throw new InsufficientStockException(shortage);
        }
        for (int i = 0; i < items.size(); i++) {
            ProductEntity p = products.get(i);
            int qty = items.get(i).getQuantity();
            p.setStock(p.getStock() - qty);
            productRepository.save(p);
        }

        OrderEntity order = new OrderEntity();
        order.setUserId(userId);
        order.setStatus(CREATED); // 8. Фиксация: статус CREATED при создании
        order.setDiscountAmount(BigDecimal.ZERO);

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (int i = 0; i < items.size(); i++) {
            ProductEntity p = products.get(i);
            OrderItemCreate item = items.get(i);
            BigDecimal priceAtOrder = p.getPrice();
            int qty = item.getQuantity();
            totalAmount = totalAmount.add(priceAtOrder.multiply(BigDecimal.valueOf(qty)));

            OrderItemEntity oi = new OrderItemEntity();
            oi.setOrder(order);
            oi.setProductId(p.getId());
            oi.setQuantity(qty);
            oi.setPriceAtOrder(priceAtOrder);
            order.getItems().add(oi);
        }

        // Промокод: проверки и расчёт скидки
        if (promoCodeStr != null && !promoCodeStr.isBlank()) {
            PromoCodeEntity promo = promoCodeRepository.findByCode(promoCodeStr.trim())
                    .orElseThrow(() -> new PromoCodeInvalidException("Промокод не найден или недействителен"));
            Instant now = Instant.now();
            if (!Boolean.TRUE.equals(promo.getActive())) {
                throw new PromoCodeInvalidException("Промокод неактивен");
            }
            if (promo.getCurrentUses() >= promo.getMaxUses()) {
                throw new PromoCodeInvalidException("Промокод исчерпан");
            }
            if (now.isBefore(promo.getValidFrom()) || now.isAfter(promo.getValidUntil())) {
                throw new PromoCodeInvalidException("Промокод недействителен по срокам");
            }
            if (totalAmount.compareTo(promo.getMinOrderAmount()) < 0) {
                throw new PromoCodeMinAmountException(
                        "Сумма заказа ниже минимальной для промокода. Минимум: " + promo.getMinOrderAmount());
            }

            BigDecimal discount;
            if (promo.getDiscountType() == DiscountType.PERCENTAGE) {
                discount = totalAmount.multiply(promo.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                BigDecimal maxDiscount = totalAmount.multiply(MAX_DISCOUNT_PERCENT).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                if (discount.compareTo(maxDiscount) > 0) {
                    discount = maxDiscount;
                }
            } else {
                discount = promo.getDiscountValue().min(totalAmount);
            }
            order.setPromoCodeId(promo.getId());
            order.setDiscountAmount(discount);
            totalAmount = totalAmount.subtract(discount);

            if (promoCodeRepository.incrementCurrentUsesIfUnderMax(promo.getId()) == 0) {
                throw new PromoCodeInvalidException("Промокод исчерпан");
            }
        }

        order.setTotalAmount(totalAmount);
        orderRepository.save(order);

        // 8. Фиксация операции и статус CREATED
        UserOperationEntity op = new UserOperationEntity();
        op.setUserId(userId);
        op.setOperationType(UserOperationType.CREATE_ORDER);
        userOperationRepository.save(op);

        return order;
    }

    /**
     * Получение заказа по ID. SELLER — запрещено; USER — только свой; ADMIN — любой.
     */
    public Optional<OrderEntity> getOrderById(UUID orderId, UUID userId, Role role) {
        if (role == Role.SELLER) {
            throw new AccessDeniedException("Роль SELLER не может просматривать заказы");
        }
        return orderRepository.findById(orderId)
                .filter(order -> role == Role.ADMIN || order.getUserId().equals(userId));
    }

    /**
     * Обновление заказа. SELLER — запрещено; USER — только свой заказ; ADMIN — любой.
     */
    @Transactional
    public OrderEntity updateOrder(UUID userId, Role role, UUID orderId, OrderUpdate orderUpdate) {
        if (role == Role.SELLER) {
            throw new AccessDeniedException("Роль SELLER не может обновлять заказы");
        }
        List<OrderItemCreate> newItems = orderUpdate.getItems();

        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // 1. Проверка владельца (ADMIN может любой)
        if (role != Role.ADMIN && !order.getUserId().equals(userId)) {
            throw new OrderOwnershipViolationException("Заказ принадлежит другому пользователю");
        }

        // 2. Проверка состояния — обновление только в CREATED
        if (order.getStatus() != CREATED) {
            throw new InvalidStateTransitionException(order.getStatus(), CREATED);
        }

        // 3. Ограничение частоты обновления
        userOperationRepository
                .findFirstByUserIdAndOperationTypeOrderByCreatedAtDesc(userId, UserOperationType.UPDATE_ORDER)
                .ifPresent(last -> {
                    if (last.getCreatedAt().plusSeconds(creationMinutesLimit * 60L).isAfter(Instant.now())) {
                        throw new OrderLimitExceededException("Превышен лимит частоты обновления заказа. Подождите " + creationMinutesLimit + " мин.");
                    }
                });

        // 4–7 в одной транзакции: блокировка товаров (старых + новых), возврат остатков, проверка и резерв новых

        List<UUID> oldIds = order.getItems().stream().map(OrderItemEntity::getProductId).distinct().toList();
        List<UUID> newIds = newItems.stream().map(OrderItemCreate::getProductId).distinct().toList();
        List<UUID> allIds = Stream.concat(oldIds.stream(), newIds.stream()).distinct().sorted().toList();
        Map<UUID, ProductEntity> productById;
        if (allIds.isEmpty()) {
            productById = Map.of();
        } else {
            List<ProductEntity> loaded = productRepository.findAllByIdForUpdate(allIds);
            productById = loaded.stream().collect(Collectors.toMap(ProductEntity::getId, p -> p));
        }
        for (OrderItemEntity oldItem : order.getItems()) {
            ProductEntity p = productById.get(oldItem.getProductId());
            if (p != null) {
                p.setStock(p.getStock() + oldItem.getQuantity());
                productRepository.save(p);
            }
        }
        List<ProductEntity> products = new ArrayList<>();
        for (OrderItemCreate item : newItems) {
            ProductEntity p = productById.get(item.getProductId());
            if (p == null) {
                throw new ProductNotFoundException(item.getProductId());
            }
            products.add(p);
        }
        for (ProductEntity p : products) {
            if (p.getStatus() != com.example.marketplace.catalog.model.ProductStatus.ACTIVE) {
                throw new ProductInactiveException(p.getId());
            }
        }
        List<InsufficientStockException.StockShortageItem> shortage = new ArrayList<>();
        for (int i = 0; i < newItems.size(); i++) {
            ProductEntity p = products.get(i);
            OrderItemCreate item = newItems.get(i);
            if (p.getStock() < item.getQuantity()) {
                shortage.add(new InsufficientStockException.StockShortageItem(p.getId(), item.getQuantity(), p.getStock()));
            }
        }
        if (!shortage.isEmpty()) {
            throw new InsufficientStockException(shortage);
        }
        for (int i = 0; i < newItems.size(); i++) {
            ProductEntity p = products.get(i);
            int qty = newItems.get(i).getQuantity();
            p.setStock(p.getStock() - qty);
            productRepository.save(p);
        }

        // Заменить позиции заказа
        order.getItems().clear();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (int i = 0; i < newItems.size(); i++) {
            ProductEntity p = products.get(i);
            OrderItemCreate item = newItems.get(i);
            BigDecimal priceAtOrder = p.getPrice();
            int qty = item.getQuantity();
            totalAmount = totalAmount.add(priceAtOrder.multiply(BigDecimal.valueOf(qty)));
            OrderItemEntity oi = new OrderItemEntity();
            oi.setOrder(order);
            oi.setProductId(p.getId());
            oi.setQuantity(qty);
            oi.setPriceAtOrder(priceAtOrder);
            order.getItems().add(oi);
        }

        // 6. Пересчёт стоимости и промокода
        order.setDiscountAmount(BigDecimal.ZERO);
        if (order.getPromoCodeId() != null) {
            PromoCodeEntity promo = promoCodeRepository.findById(order.getPromoCodeId()).orElse(null);
            boolean applied = false;
            if (promo != null && totalAmount.compareTo(promo.getMinOrderAmount()) >= 0) {
                Instant now = Instant.now();
                if (Boolean.TRUE.equals(promo.getActive()) && promo.getCurrentUses() < promo.getMaxUses()
                        && !now.isBefore(promo.getValidFrom()) && !now.isAfter(promo.getValidUntil())) {
                    BigDecimal discount;
                    if (promo.getDiscountType() == DiscountType.PERCENTAGE) {
                        discount = totalAmount.multiply(promo.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                        BigDecimal maxDiscount = totalAmount.multiply(MAX_DISCOUNT_PERCENT).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                        if (discount.compareTo(maxDiscount) > 0) discount = maxDiscount;
                    } else {
                        discount = promo.getDiscountValue().min(totalAmount);
                    }
                    order.setDiscountAmount(discount);
                    totalAmount = totalAmount.subtract(discount);
                    applied = true;
                }
            }
            if (!applied && order.getPromoCodeId() != null) {
                promoCodeRepository.decrementCurrentUsesIfPositive(order.getPromoCodeId());
                order.setPromoCodeId(null);
            }
        }
        order.setTotalAmount(totalAmount);

        orderRepository.save(order);

        // 7. Фиксация операции
        UserOperationEntity op = new UserOperationEntity();
        op.setUserId(userId);
        op.setOperationType(UserOperationType.UPDATE_ORDER);
        userOperationRepository.save(op);

        return order;
    }

    /**
     * Отмена заказа. SELLER — запрещено; USER — только свой; ADMIN — любой.
     */
    @Transactional
    public OrderEntity cancelOrder(UUID userId, Role role, UUID orderId) {
        if (role == Role.SELLER) {
            throw new AccessDeniedException("Роль SELLER не может отменять заказы");
        }
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // 1. Проверка владельца (ADMIN может любой)
        if (role != Role.ADMIN && !order.getUserId().equals(userId)) {
            throw new OrderOwnershipViolationException("Заказ принадлежит другому пользователю");
        }

        // 2. Проверка состояния — отмена только из CREATED или PAYMENT_PENDING
        OrderStateMachine.validateTransition(order.getStatus(), OrderStatus.CANCELED);

        // 3. Возврат остатков (блокировка товаров FOR UPDATE)
        List<UUID> productIds = order.getItems().stream().map(OrderItemEntity::getProductId).distinct().sorted().toList();
        if (!productIds.isEmpty()) {
            List<ProductEntity> loaded = productRepository.findAllByIdForUpdate(productIds);
            Map<UUID, ProductEntity> productById = loaded.stream().collect(Collectors.toMap(ProductEntity::getId, p -> p));
            for (OrderItemEntity item : order.getItems()) {
                ProductEntity p = productById.get(item.getProductId());
                if (p != null) {
                    p.setStock(p.getStock() + item.getQuantity());
                    productRepository.save(p);
                }
            }
        }

        // 4. Возврат использования промокода (атомарный декремент, не уходит в минус при гонках)
        if (order.getPromoCodeId() != null) {
            promoCodeRepository.decrementCurrentUsesIfPositive(order.getPromoCodeId());
        }

        // 5. Установка статуса
        order.setStatus(OrderStatus.CANCELED);
        orderRepository.save(order);

        return order;
    }
}
