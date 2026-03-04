package com.example.marketplace.catalog.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_ROLE = "role";
    private static final int MIN_SECRET_BYTES = 32; // HS256

    private final SecretKey accessSecret;
    private final SecretKey refreshSecret;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtService(
            @Value("${jwt.access.secret:default-access-secret-min-256-bits-for-hs256!!!!!!!!!!!!!!!!!!!!!!!!!!!!}") String accessSecret,
            @Value("${jwt.refresh.secret:default-refresh-secret-min-256-bits-for-hs256!!!!!!!!!!!!!!!!!!!!!!!!!!}") String refreshSecret,
            @Value("${jwt.access.expiration-minutes:20}") long accessExpirationMinutes,
            @Value("${jwt.refresh.expiration-days:14}") long refreshExpirationDays) {
        this.accessSecret = toSecretKey(accessSecret, "JWT_ACCESS_SECRET");
        this.refreshSecret = toSecretKey(refreshSecret, "JWT_REFRESH_SECRET");
        this.accessExpirationMs = accessExpirationMinutes * 60 * 1000;
        this.refreshExpirationMs = refreshExpirationDays * 24 * 60 * 60 * 1000;
    }

    private static SecretKey toSecretKey(String secret, String envVarName) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "JWT secret is empty. Set " + envVarName + " in .env (min 32 characters). " +
                "For Docker: .env must be next to docker-compose.yml.");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                envVarName + " must be at least " + MIN_SECRET_BYTES + " bytes (got " + bytes.length + "). " +
                "Use a longer secret for HS256.");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public String generateAccessToken(UUID userId, String email, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_USER_ID, userId.toString())
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLE, role != null ? role.name() : Role.USER.name())
                .claim(CLAIM_TYPE, TokenType.ACCESS.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessExpirationMs)))
                .signWith(this.accessSecret)
                .compact();
    }

    public String generateRefreshToken(UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_USER_ID, userId.toString())
                .claim(CLAIM_TYPE, TokenType.REFRESH.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(refreshExpirationMs)))
                .signWith(this.refreshSecret)
                .compact();
    }

    public long getAccessExpirationSeconds() {
        return accessExpirationMs / 1000;
    }

    /**
     * Валидирует access token. При истечении срока — ExpiredJwtException, при невалидном — JwtException.
     */
    public Claims validateAccessToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(accessSecret)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new TokenExpiredException("Access token expired", e);
        } catch (JwtException e) {
            throw new TokenInvalidException("Invalid access token", e);
        }
    }

    /**
     * Валидирует refresh token. При невалидном/просроченном — RefreshTokenInvalidException.
     */
    public Claims validateRefreshToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(refreshSecret)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            throw new RefreshTokenInvalidException("Invalid or expired refresh token", e);
        }
    }

    public static UUID getUserIdFromClaims(Claims claims) {
        String userId = claims.get(CLAIM_USER_ID, String.class);
        return userId != null ? UUID.fromString(userId) : UUID.fromString(claims.getSubject());
    }

    public static Role getRoleFromClaims(Claims claims) {
        String roleStr = claims.get(CLAIM_ROLE, String.class);
        if (roleStr == null) return Role.USER;
        try {
            return Role.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            return Role.USER;
        }
    }

    public static class TokenExpiredException extends RuntimeException {
        public TokenExpiredException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class TokenInvalidException extends RuntimeException {
        public TokenInvalidException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class RefreshTokenInvalidException extends RuntimeException {
        public RefreshTokenInvalidException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
