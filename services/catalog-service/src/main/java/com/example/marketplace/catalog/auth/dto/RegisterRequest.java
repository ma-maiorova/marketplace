package com.example.marketplace.catalog.auth.dto;

import com.example.marketplace.catalog.auth.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank(message = "email обязателен")
    @Email(message = "Некорректный формат email")
    private String email;

    @NotBlank(message = "password обязателен")
    @Size(min = 6, message = "Пароль не менее 6 символов")
    private String password;

    /** Опционально: USER (по умолчанию), SELLER или ADMIN. */
    private String role;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Role getRoleAsEnum() {
        if (role == null || role.isBlank()) return Role.USER;
        try {
            return Role.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Role.USER;
        }
    }
}
