package com.example.marketplace.catalog.service;

import com.example.marketplace.catalog.auth.Role;
import com.example.marketplace.catalog.entity.ProductEntity;
import com.example.marketplace.catalog.exception.AccessDeniedException;
import com.example.marketplace.catalog.model.ProductCreate;
import com.example.marketplace.catalog.model.ProductStatus;
import com.example.marketplace.catalog.model.ProductUpdate;
import com.example.marketplace.catalog.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Сервис CRUD для товаров. Мягкое удаление — перевод статуса в ARCHIVED.
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Создание товара. USER — запрещено; SELLER/ADMIN — разрешено, seller_id = userId.
     */
    @Transactional
    public ProductEntity create(UUID userId, Role role, ProductCreate create) {
        if (role == Role.USER) {
            throw new AccessDeniedException("Роль USER не может создавать товары");
        }
        ProductEntity entity = new ProductEntity();
        entity.setName(create.getName());
        entity.setDescription(create.getDescription() != null && create.getDescription().isPresent()
                ? create.getDescription().get() : null);
        entity.setPrice(create.getPrice());
        entity.setStock(create.getStock());
        entity.setCategory(create.getCategory());
        entity.setStatus(create.getStatus());
        entity.setSellerId(userId);
        return productRepository.save(entity);
    }

    public Optional<ProductEntity> findById(UUID id) {
        return productRepository.findById(id);
    }

    public Page<ProductEntity> findAll(Integer page, Integer size, ProductStatus status, String category) {
        int pageNum = page != null ? page : 0;
        int sizeNum = size != null ? size : 20;
        Pageable pageable = PageRequest.of(pageNum, sizeNum);

        if (status != null && category != null && !category.isBlank()) {
            return productRepository.findByStatusAndCategory(status, category.trim(), pageable);
        }
        if (status != null) {
            return productRepository.findByStatus(status, pageable);
        }
        if (category != null && !category.isBlank()) {
            return productRepository.findByCategory(category.trim(), pageable);
        }
        return productRepository.findAll(pageable);
    }

    /**
     * Все товары для выгрузки (CSV) с опциональной фильтрацией по status и category.
     */
    public List<ProductEntity> findAllForExport(ProductStatus status, String category) {
        if (status != null && category != null && !category.isBlank()) {
            return productRepository.findAllByStatusAndCategory(status, category.trim());
        }
        if (status != null) {
            return productRepository.findAllByStatus(status);
        }
        if (category != null && !category.isBlank()) {
            return productRepository.findAllByCategory(category.trim());
        }
        return productRepository.findAll();
    }

    /**
     * Обновление товара. USER — запрещено; SELLER — только свои (seller_id = userId); ADMIN — любые.
     */
    @Transactional
    public Optional<ProductEntity> update(UUID id, UUID userId, Role role, ProductUpdate update) {
        if (role == Role.USER) {
            throw new AccessDeniedException("Роль USER не может изменять товары");
        }
        Optional<ProductEntity> opt = role == Role.ADMIN
                ? productRepository.findById(id)
                : productRepository.findByIdAndSellerId(id, userId);
        return opt.map(entity -> {
            if (update.getName() != null) {
                entity.setName(update.getName());
            }
            if (update.getDescription() != null) {
                entity.setDescription(update.getDescription().isPresent()
                        ? update.getDescription().get() : null);
            }
            if (update.getPrice() != null) {
                entity.setPrice(update.getPrice());
            }
            if (update.getStock() != null) {
                entity.setStock(update.getStock());
            }
            if (update.getCategory() != null) {
                entity.setCategory(update.getCategory());
            }
            if (update.getStatus() != null) {
                entity.setStatus(update.getStatus());
            }
            return productRepository.save(entity);
        });
    }

    /**
     * Мягкое удаление. USER — запрещено; SELLER — только свои; ADMIN — любые.
     */
    @Transactional
    public boolean softDelete(UUID id, UUID userId, Role role) {
        if (role == Role.USER) {
            throw new AccessDeniedException("Роль USER не может удалять товары");
        }
        Optional<ProductEntity> opt = role == Role.ADMIN
                ? productRepository.findById(id)
                : productRepository.findByIdAndSellerId(id, userId);
        return opt.map(entity -> {
            entity.setStatus(ProductStatus.ARCHIVED);
            productRepository.save(entity);
            return true;
        }).orElse(false);
    }
}
