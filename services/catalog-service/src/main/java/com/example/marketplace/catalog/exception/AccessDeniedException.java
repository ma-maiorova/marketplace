package com.example.marketplace.catalog.exception;

/**
 * Недостаточно прав для операции (ролевая модель). Обработчик возвращает 403 с error_code: ACCESS_DENIED.
 */
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException() {
        super("Недостаточно прав для выполнения операции");
    }

    public AccessDeniedException(String message) {
        super(message);
    }
}
