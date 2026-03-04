package com.example.marketplace.catalog.exception;

import com.example.marketplace.catalog.auth.JwtService;
import com.example.marketplace.catalog.model.ApiError;
import com.example.marketplace.catalog.model.InsufficientStockDetails;
import com.example.marketplace.catalog.model.InsufficientStockDetailsItemsInner;
import com.example.marketplace.catalog.model.ValidationErrorDetails;
import com.example.marketplace.catalog.model.ValidationErrorDetailsFieldsInner;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Контрактная обработка ошибок: все ответы об ошибках в формате ApiError (error_code, message, details).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final String PRODUCT_NOT_FOUND_MESSAGE = "Товар не найден";

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiError> handleProductNotFound(ProductNotFoundException ex) {
        ApiError error = new ApiError();
        error.setErrorCode(ApiError.ErrorCodeEnum.PRODUCT_NOT_FOUND);
        error.setMessage(PRODUCT_NOT_FOUND_MESSAGE);
        error.setDetails(JsonNullable.undefined());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Ошибки валидации тела запроса (Bean Validation): 400, VALIDATION_ERROR, details — список полей и нарушений.
     * Невалидные запросы не доходят до бизнес-логики.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<ValidationErrorDetailsFieldsInner> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> {
                    ValidationErrorDetailsFieldsInner item = new ValidationErrorDetailsFieldsInner();
                    item.setField(fe.getField());
                    item.setViolation(fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid");
                    return item;
                })
                .collect(Collectors.toList());
        ValidationErrorDetails details = new ValidationErrorDetails();
        details.setFields(fields);

        ApiError error = new ApiError();
        error.setErrorCode(ApiError.ErrorCodeEnum.VALIDATION_ERROR);
        error.setMessage("Ошибка валидации входных данных");
        error.setDetails(JsonNullable.of(details));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Ошибки валидации path/query (например, неверный формат UUID): 400, VALIDATION_ERROR.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        List<ValidationErrorDetailsFieldsInner> fields = ex.getConstraintViolations().stream()
                .map(v -> {
                    ValidationErrorDetailsFieldsInner item = new ValidationErrorDetailsFieldsInner();
                    String path = v.getPropertyPath() != null ? v.getPropertyPath().toString() : "parameter";
                    item.setField(path);
                    item.setViolation(v.getMessage() != null ? v.getMessage() : "invalid");
                    return item;
                })
                .collect(Collectors.toList());
        ValidationErrorDetails details = new ValidationErrorDetails();
        details.setFields(fields);

        ApiError error = new ApiError();
        error.setErrorCode(ApiError.ErrorCodeEnum.VALIDATION_ERROR);
        error.setMessage("Ошибка валидации входных данных");
        error.setDetails(JsonNullable.of(details));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(OrderLimitExceededException.class)
    public ResponseEntity<ApiError> handleOrderLimitExceeded(OrderLimitExceededException ex) {
        ApiError error = new ApiError();
        error.setErrorCode(ApiError.ErrorCodeEnum.ORDER_LIMIT_EXCEEDED);
        error.setMessage(ex.getMessage() != null ? ex.getMessage() : "Превышен лимит частоты создания заказа");
        error.setDetails(JsonNullable.undefined());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error);
    }

    @ExceptionHandler(OrderHasActiveException.class)
    public ResponseEntity<ApiError> handleOrderHasActive(OrderHasActiveException ex) {
        ApiError error = new ApiError();
        error.setErrorCode(ApiError.ErrorCodeEnum.ORDER_HAS_ACTIVE);
        error.setMessage(ex.getMessage() != null ? ex.getMessage() : "У пользователя уже есть активный заказ");
        error.setDetails(JsonNullable.undefined());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(ProductInactiveException.class)
    public ResponseEntity<ApiError> handleProductInactive(ProductInactiveException ex) {
        ApiError error = new ApiError();
        error.setErrorCode(ApiError.ErrorCodeEnum.PRODUCT_INACTIVE);
        error.setMessage(ex.getMessage() != null ? ex.getMessage() : "Товар неактивен");
        error.setDetails(JsonNullable.undefined());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiError> handleInsufficientStock(InsufficientStockException ex) {
        List<InsufficientStockDetailsItemsInner> detailItems = new ArrayList<>();
        for (InsufficientStockException.StockShortageItem item : ex.getItems()) {
            InsufficientStockDetailsItemsInner inner = new InsufficientStockDetailsItemsInner();
            inner.setProductId(item.productId());
            inner.setRequestedQuantity(item.requestedQuantity());
            inner.setAvailableStock(item.availableStock());
            detailItems.add(inner);
        }
        InsufficientStockDetails details = new InsufficientStockDetails();
        details.setItems(detailItems);

        ApiError error = new ApiError();
        error.setErrorCode(ApiError.ErrorCodeEnum.INSUFFICIENT_STOCK);
        error.setMessage(ex.getMessage() != null ? ex.getMessage() : "Недостаточно товара на складе");
        error.setDetails(JsonNullable.of(details));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(PromoCodeInvalidException.class)
    public ResponseEntity<ApiError> handlePromoCodeInvalid(PromoCodeInvalidException ex) {
        ApiError error = new ApiError();
        error.setErrorCode(ApiError.ErrorCodeEnum.PROMO_CODE_INVALID);
        error.setMessage(ex.getMessage() != null ? ex.getMessage() : "Промокод не найден или недействителен");
        error.setDetails(JsonNullable.undefined());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    @ExceptionHandler(PromoCodeMinAmountException.class)
    public ResponseEntity<ApiError> handlePromoCodeMinAmount(PromoCodeMinAmountException ex) {
        ApiError error = new ApiError();
        error.setErrorCode(ApiError.ErrorCodeEnum.PROMO_CODE_MIN_AMOUNT);
        error.setMessage(ex.getMessage() != null ? ex.getMessage() : "Сумма заказа ниже минимальной для промокода");
        error.setDetails(JsonNullable.undefined());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiError> handleOrderNotFound(OrderNotFoundException ex) {
        ApiError error = new ApiError();
        error.setErrorCode(ApiError.ErrorCodeEnum.ORDER_NOT_FOUND);
        error.setMessage(ex.getMessage() != null ? ex.getMessage() : "Заказ не найден");
        error.setDetails(JsonNullable.undefined());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ApiError> handleInvalidStateTransition(InvalidStateTransitionException ex) {
        ApiError error = new ApiError();
        error.setErrorCode(ApiError.ErrorCodeEnum.INVALID_STATE_TRANSITION);
        error.setMessage(ex.getMessage() != null ? ex.getMessage() : "Недопустимый переход состояния заказа");
        error.setDetails(JsonNullable.undefined());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(OrderOwnershipViolationException.class)
    public ResponseEntity<ApiError> handleOrderOwnershipViolation(OrderOwnershipViolationException ex) {
        ApiError error = new ApiError();
        error.setErrorCode(ApiError.ErrorCodeEnum.ORDER_OWNERSHIP_VIOLATION);
        error.setMessage(ex.getMessage() != null ? ex.getMessage() : "Заказ принадлежит другому пользователю");
        error.setDetails(JsonNullable.undefined());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(JwtService.RefreshTokenInvalidException.class)
    public ResponseEntity<ApiError> handleRefreshTokenInvalid(JwtService.RefreshTokenInvalidException ex) {
        ApiError error = new ApiError();
        error.setErrorCode(ApiError.ErrorCodeEnum.REFRESH_TOKEN_INVALID);
        error.setMessage(ex.getMessage() != null ? ex.getMessage() : "Невалидный или просроченный refresh token");
        error.setDetails(JsonNullable.undefined());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(com.example.marketplace.catalog.exception.AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(com.example.marketplace.catalog.exception.AccessDeniedException ex) {
        ApiError error = new ApiError();
        error.setErrorCode(ApiError.ErrorCodeEnum.ACCESS_DENIED);
        error.setMessage(ex.getMessage() != null ? ex.getMessage() : "Недостаточно прав для выполнения операции");
        error.setDetails(JsonNullable.undefined());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }
}
