package com.example.marketplace.catalog.auth;

import com.example.marketplace.catalog.api.AuthApiDelegate;
import com.example.marketplace.catalog.model.AuthError;
import com.example.marketplace.catalog.model.AuthTokenResponse;
import com.example.marketplace.catalog.model.LoginRequest;
import com.example.marketplace.catalog.model.RefreshRequest;
import com.example.marketplace.catalog.model.RegisterRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class AuthApiDelegateImpl implements AuthApiDelegate {

    private final AuthService authService;

    public AuthApiDelegateImpl(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public ResponseEntity<AuthTokenResponse> register(RegisterRequest registerRequest) {
        try {
            Role role = mapRole(registerRequest.getRole());
            AuthTokenResponse response = authService.register(
                    registerRequest.getEmail(),
                    registerRequest.getPassword(),
                    role
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (AuthService.EmailAlreadyExistsException e) {
            return errorResponse(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @Override
    public ResponseEntity<AuthTokenResponse> login(LoginRequest loginRequest) {
        try {
            AuthTokenResponse response = authService.login(
                    loginRequest.getEmail(),
                    loginRequest.getPassword()
            );
            return ResponseEntity.ok(response);
        } catch (AuthService.BadCredentialsException e) {
            return errorResponse(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    @Override
    public ResponseEntity<AuthTokenResponse> refresh(RefreshRequest refreshRequest) {
        try {
            AuthTokenResponse response = authService.refresh(refreshRequest.getRefreshToken());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(HttpStatus.UNAUTHORIZED, "Невалидный или истёкший refresh token");
        }
    }

    private static Role mapRole(RegisterRequest.RoleEnum roleEnum) {
        if (roleEnum == null) return Role.USER;
        return switch (roleEnum) {
            case USER -> Role.USER;
            case SELLER -> Role.SELLER;
            case ADMIN -> Role.ADMIN;
        };
    }

    @SuppressWarnings("unchecked")
    private static ResponseEntity<AuthTokenResponse> errorResponse(HttpStatus status, String message) {
        return (ResponseEntity<AuthTokenResponse>) (ResponseEntity<?>) ResponseEntity
                .status(status)
                .body(new AuthError().message(message));
    }
}
