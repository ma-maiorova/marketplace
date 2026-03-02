package com.example.marketplace.catalog.api;

import com.example.marketplace.catalog.auth.UserPrincipal;
import com.example.marketplace.catalog.entity.OrderEntity;
import com.example.marketplace.catalog.entity.OrderItemEntity;
import com.example.marketplace.catalog.exception.OrderNotFoundException;
import com.example.marketplace.catalog.model.OrderCreate;
import com.example.marketplace.catalog.model.OrderItemResponse;
import com.example.marketplace.catalog.model.OrderResponse;
import com.example.marketplace.catalog.model.OrderUpdate;
import com.example.marketplace.catalog.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrdersApiDelegateImpl implements OrdersApiDelegate {

    private final OrderService orderService;

    public OrdersApiDelegateImpl(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public ResponseEntity<OrderResponse> createOrder(UUID xUserId, OrderCreate orderCreate) {
        UserPrincipal principal = getPrincipal();
        OrderEntity order = orderService.createOrder(principal.getUserId(), principal.getRole(), orderCreate);
        return ResponseEntity.status(HttpStatus.CREATED).body(toOrderResponse(order));
    }

    @Override
    public ResponseEntity<OrderResponse> updateOrder(UUID xUserId, UUID id, OrderUpdate orderUpdate) {
        UserPrincipal principal = getPrincipal();
        OrderEntity order = orderService.updateOrder(principal.getUserId(), principal.getRole(), id, orderUpdate);
        return ResponseEntity.ok(toOrderResponse(order));
    }

    @Override
    public ResponseEntity<OrderResponse> getOrder(UUID xUserId, UUID id) {
        UserPrincipal principal = getPrincipal();
        return orderService.getOrderById(id, principal.getUserId(), principal.getRole())
                .map(order -> ResponseEntity.ok(toOrderResponse(order)))
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Override
    public ResponseEntity<OrderResponse> cancelOrder(UUID xUserId, UUID id) {
        UserPrincipal principal = getPrincipal();
        OrderEntity order = orderService.cancelOrder(principal.getUserId(), principal.getRole(), id);
        return ResponseEntity.ok(toOrderResponse(order));
    }

    private static UserPrincipal getPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal p)) {
            throw new com.example.marketplace.catalog.exception.AccessDeniedException("Требуется аутентификация");
        }
        return p;
    }

    private OrderResponse toOrderResponse(OrderEntity order) {
        OrderResponse r = new OrderResponse();
        r.setId(order.getId());
        r.setUserId(order.getUserId());
        r.setStatus(order.getStatus());
        r.setTotalAmount(order.getTotalAmount());
        r.setDiscountAmount(order.getDiscountAmount());
        List<OrderItemResponse> items = order.getItems().stream()
                .map(this::toOrderItemResponse)
                .collect(Collectors.toList());
        r.setItems(items);
        r.setCreatedAt(order.getCreatedAt().atOffset(ZoneOffset.UTC));
        r.setUpdatedAt(order.getUpdatedAt().atOffset(ZoneOffset.UTC));
        return r;
    }

    private OrderItemResponse toOrderItemResponse(OrderItemEntity item) {
        OrderItemResponse r = new OrderItemResponse();
        r.setProductId(item.getProductId());
        r.setQuantity(item.getQuantity());
        r.setPriceAtOrder(item.getPriceAtOrder());
        return r;
    }
}
