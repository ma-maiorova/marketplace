package com.example.marketplace.catalog.api;

import com.example.marketplace.catalog.auth.Role;
import com.example.marketplace.catalog.auth.UserPrincipal;
import com.example.marketplace.catalog.entity.ProductEntity;
import com.example.marketplace.catalog.model.ProductCreate;
import com.example.marketplace.catalog.model.ProductPage;
import com.example.marketplace.catalog.model.ProductResponse;
import com.example.marketplace.catalog.model.ProductStatus;
import com.example.marketplace.catalog.model.ProductUpdate;
import com.example.marketplace.catalog.exception.ProductNotFoundException;
import com.example.marketplace.catalog.service.ProductService;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Реализация сгенерированного API делегата. Использует только сгенерированные DTO (ProductCreate, ProductUpdate, ProductResponse, ProductPage).
 */
@Service
public class ProductsApiDelegateImpl implements ProductsApiDelegate {

    private final ProductService productService;

    public ProductsApiDelegateImpl(ProductService productService) {
        this.productService = productService;
    }

    @Override
    public ResponseEntity<ProductResponse> createProduct(ProductCreate productCreate) {
        UserPrincipal principal = getPrincipal();
        ProductEntity entity = productService.create(principal.getUserId(), principal.getRole(), productCreate);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(entity));
    }

    @Override
    public ResponseEntity<ProductResponse> getProductById(UUID id) {
        return productService.findById(id)
                .map(entity -> ResponseEntity.ok(toResponse(entity)))
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Override
    public ResponseEntity<ProductPage> getProducts(Integer page, Integer size, ProductStatus status, String category) {
        var entityPage = productService.findAll(page, size, status, category);
        ProductPage result = new ProductPage();
        result.setContent(entityPage.getContent().stream().map(this::toResponse).collect(Collectors.toList()));
        result.setTotalElements((int) entityPage.getTotalElements());
        result.setPage(entityPage.getNumber());
        result.setSize(entityPage.getSize());
        return ResponseEntity.ok(result);
    }

    @Override
    public ResponseEntity<ProductResponse> updateProduct(UUID id, ProductUpdate productUpdate) {
        UserPrincipal principal = getPrincipal();
        return productService.update(id, principal.getUserId(), principal.getRole(), productUpdate)
                .map(entity -> ResponseEntity.ok(toResponse(entity)))
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Override
    public ResponseEntity<Void> deleteProduct(UUID id) {
        UserPrincipal principal = getPrincipal();
        if (!productService.softDelete(id, principal.getUserId(), principal.getRole())) {
            throw new ProductNotFoundException(id);
        }
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Resource> exportProducts(ProductStatus status, String category) {
        List<ProductEntity> products = productService.findAllForExport(status, category);
        String csv = buildProductsCsv(products);
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        Resource resource = new ByteArrayResource(bytes);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "products.csv");
        headers.setContentLength(bytes.length);
        return ResponseEntity.ok().headers(headers).body(resource);
    }

    @Override
    public ResponseEntity<Resource> exportProductsStream(ProductStatus status, String category) {
        try {
            PipedOutputStream out = new PipedOutputStream();
            PipedInputStream in = new PipedInputStream(out);
            Thread writer = new Thread(() -> {
                try (OutputStream os = out) {
                    os.write("id,name,description,price,stock,category,status,created_at,updated_at\n".getBytes(StandardCharsets.UTF_8));
                    productService.forEachProductForExport(status, category, 100, e -> {
                        try {
                            String row = escapeCsv(e.getId().toString()) + ','
                                    + escapeCsv(e.getName()) + ','
                                    + escapeCsv(e.getDescription() != null ? e.getDescription() : "") + ','
                                    + e.getPrice() + ','
                                    + e.getStock() + ','
                                    + escapeCsv(e.getCategory()) + ','
                                    + e.getStatus().getValue() + ','
                                    + escapeCsv(e.getCreatedAt().toString()) + ','
                                    + escapeCsv(e.getUpdatedAt().toString()) + '\n';
                            os.write(row.getBytes(StandardCharsets.UTF_8));
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            writer.start();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/csv"));
            headers.setContentDispositionFormData("attachment", "products.csv");
            return ResponseEntity.ok().headers(headers).body(new InputStreamResource(in));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create streaming export", e);
        }
    }

    private String buildProductsCsv(List<ProductEntity> products) {
        StringBuilder sb = new StringBuilder();
        sb.append("id,name,description,price,stock,category,status,created_at,updated_at\n");
        for (ProductEntity e : products) {
            sb.append(escapeCsv(e.getId().toString())).append(',');
            sb.append(escapeCsv(e.getName())).append(',');
            sb.append(escapeCsv(e.getDescription())).append(',');
            sb.append(e.getPrice()).append(',');
            sb.append(e.getStock()).append(',');
            sb.append(escapeCsv(e.getCategory())).append(',');
            sb.append(e.getStatus().getValue()).append(',');
            sb.append(escapeCsv(e.getCreatedAt().toString())).append(',');
            sb.append(escapeCsv(e.getUpdatedAt().toString())).append('\n');
        }
        return sb.toString();
    }

    private static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static UserPrincipal getPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal p)) {
            throw new com.example.marketplace.catalog.exception.AccessDeniedException("Требуется аутентификация");
        }
        return p;
    }

    private ProductResponse toResponse(ProductEntity entity) {
        ProductResponse r = new ProductResponse();
        r.setId(entity.getId());
        r.setName(entity.getName());
        r.setDescription(entity.getDescription() != null ? JsonNullable.of(entity.getDescription()) : JsonNullable.undefined());
        r.setPrice(entity.getPrice());
        r.setStock(entity.getStock());
        r.setCategory(entity.getCategory());
        r.setStatus(entity.getStatus());
        r.setCreatedAt(entity.getCreatedAt().atOffset(ZoneOffset.UTC));
        r.setUpdatedAt(entity.getUpdatedAt().atOffset(ZoneOffset.UTC));
        return r;
    }
}
