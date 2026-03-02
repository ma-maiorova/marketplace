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
import java.util.Optional;
import java.util.UUID;

/**
 * Создание заказа с последовательными проверками (1–7) и созданием в одной транзакции.
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final UserOperationRepository userOperationRepository;

    @Value("${order.creation.minutes-limit:5}")
    private int creationMinutesLimit;

    private static final OrderStatus CREATED = OrderStatus.CREATED;
    private static final OrderStatus PAYMENT_PENDING = OrderStatus.PAYMENT_PENDING;
    private static final BigDecimal MAX_DISCOUNT_PERCENT = new BigDecimal("70");

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        ProductRepository productRepository,
                        PromoCodeRepository promoCodeRepository,
                        UserOperationRepository userOperationRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
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

        // 3. Проверка каталога: товар существует и ACTIVE
        List<ProductEntity> products = new ArrayList<>();
        for (OrderItemCreate item : items) {
            ProductEntity p = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException(item.getProductId()));
            if (p.getStatus() != com.example.marketplace.catalog.model.ProductStatus.ACTIVE) {
                throw new ProductInactiveException(p.getId());
            }
            products.add(p);
        }

        // 4. Проверка остатков
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

        // 5–7 в одной транзакции: резерв остатков, создание заказа, снапшот цен, расчёт суммы, промо, user_operation

        // Резервирование остатков
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

            promo.setCurrentUses(promo.getCurrentUses() + 1);
            promoCodeRepository.save(promo);
        }

        order.setTotalAmount(totalAmount);
        orderRepository.save(order);
        for (OrderItemEntity oi : order.getItems()) {
            orderItemRepository.save(oi);
        }

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

        // 4–7 в одной транзакции: возврат остатков, проверка и резерв новых позиций, пересчёт, промо, user_operation

        // 4. Возврат предыдущих остатков
        for (OrderItemEntity oldItem : order.getItems()) {
            productRepository.findById(oldItem.getProductId()).ifPresent(p -> {
                p.setStock(p.getStock() + oldItem.getQuantity());
                productRepository.save(p);
            });
        }

        // 5. Проверка новых позиций (каталог + остатки) и резервирование
        List<ProductEntity> products = new ArrayList<>();
        for (OrderItemCreate item : newItems) {
            ProductEntity p = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException(item.getProductId()));
            if (p.getStatus() != com.example.marketplace.catalog.model.ProductStatus.ACTIVE) {
                throw new ProductInactiveException(p.getId());
            }
            products.add(p);
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
            if (!applied && promo != null) {
                promo.setCurrentUses(promo.getCurrentUses() - 1);
                promoCodeRepository.save(promo);
                order.setPromoCodeId(null);
            } else if (!applied) {
                order.setPromoCodeId(null);
            }
        }
        order.setTotalAmount(totalAmount);

        orderRepository.save(order);
        for (OrderItemEntity oi : order.getItems()) {
            orderItemRepository.save(oi);
        }

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

        // 3. Возврат остатков
        for (OrderItemEntity item : order.getItems()) {
            productRepository.findById(item.getProductId()).ifPresent(p -> {
                p.setStock(p.getStock() + item.getQuantity());
                productRepository.save(p);
            });
        }

        // 4. Возврат использования промокода
        if (order.getPromoCodeId() != null) {
            promoCodeRepository.findById(order.getPromoCodeId()).ifPresent(promo -> {
                promo.setCurrentUses(promo.getCurrentUses() - 1);
                promoCodeRepository.save(promo);
            });
        }

        // 5. Установка статуса
        order.setStatus(OrderStatus.CANCELED);
        orderRepository.save(order);

        return order;
    }
}
