package com.example.marketplace.catalog.auth;

import com.example.marketplace.catalog.auth.dto.LoginRequest;
import com.example.marketplace.catalog.auth.dto.RefreshRequest;
import com.example.marketplace.catalog.auth.dto.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            AuthService.AuthResponse response = authService.register(request.getEmail(), request.getPassword(), request.getRoleAsEnum());
            return ResponseEntity.status(HttpStatus.CREATED).body(toJson(response));
        } catch (AuthService.EmailAlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthService.AuthResponse response = authService.login(request.getEmail(), request.getPassword());
            return ResponseEntity.ok(toJson(response));
        } catch (AuthService.BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthService.AuthResponse response = authService.refresh(request.getRefreshToken());
        return ResponseEntity.ok(toJson(response));
    }

    private static Map<String, Object> toJson(AuthService.AuthResponse response) {
        return Map.of(
                "access_token", response.getAccessToken(),
                "refresh_token", response.getRefreshToken(),
                "expires_in", response.getExpiresIn()
        );
    }
}
