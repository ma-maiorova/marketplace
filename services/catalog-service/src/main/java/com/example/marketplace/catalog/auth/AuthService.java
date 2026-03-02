package com.example.marketplace.catalog.auth;

import com.example.marketplace.catalog.auth.Role;
import com.example.marketplace.catalog.entity.UserEntity;
import com.example.marketplace.catalog.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(String email, String password, Role role) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("Пользователь с таким email уже зарегистрирован");
        }
        Role assignedRole = role != null ? role : Role.USER;
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail(email.toLowerCase().trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setCreatedAt(java.time.Instant.now());
        user.setRole(assignedRole);
        userRepository.save(user);
        return buildTokens(user);
    }

    public AuthResponse login(String email, String password) {
        UserEntity user = userRepository.findByEmail(email.trim())
                .orElseThrow(() -> new BadCredentialsException("Неверный email или пароль"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Неверный email или пароль");
        }
        return buildTokens(user);
    }

    public AuthResponse refresh(String refreshToken) {
        Claims claims = jwtService.validateRefreshToken(refreshToken);
        UUID userId = JwtService.getUserIdFromClaims(claims);
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new JwtService.RefreshTokenInvalidException("User not found", null));
        Role role = user.getRole() != null ? user.getRole() : Role.USER;
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), role);
        String newRefreshToken = jwtService.generateRefreshToken(user.getId());
        return new AuthResponse(accessToken, newRefreshToken, jwtService.getAccessExpirationSeconds());
    }

    private AuthResponse buildTokens(UserEntity user) {
        Role role = user.getRole() != null ? user.getRole() : Role.USER;
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), role);
        String refreshToken = jwtService.generateRefreshToken(user.getId());
        return new AuthResponse(accessToken, refreshToken, jwtService.getAccessExpirationSeconds());
    }

    public static class AuthResponse {
        private final String accessToken;
        private final String refreshToken;
        private final long expiresIn;

        public AuthResponse(String accessToken, String refreshToken, long expiresIn) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresIn = expiresIn;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public long getExpiresIn() {
            return expiresIn;
        }
    }

    public static class EmailAlreadyExistsException extends RuntimeException {
        public EmailAlreadyExistsException(String message) {
            super(message);
        }
    }

    public static class BadCredentialsException extends RuntimeException {
        public BadCredentialsException(String message) {
            super(message);
        }
    }
}
