package com.example.marketplace.catalog.config;

import com.example.marketplace.catalog.auth.JwtService;
import com.example.marketplace.catalog.auth.Role;
import com.example.marketplace.catalog.auth.UserPrincipal;
import com.example.marketplace.catalog.model.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Проверяет JWT в заголовке Authorization и устанавливает SecurityContext.
 * Не выполняется для путей без обязательной авторизации: /auth/**, /health, /swagger-ui/**, /v3/api-docs/** —
 * их можно открывать без токена; из Swagger UI запросы к API по-прежнему отправляются с токеном (Authorize).
 * При невалидном/просроченном токене возвращает 401 с error_code TOKEN_EXPIRED или TOKEN_INVALID.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            sendTokenError(response, ApiError.ErrorCodeEnum.TOKEN_INVALID, "Отсутствует или неверный формат токена");
            return;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            sendTokenError(response, ApiError.ErrorCodeEnum.TOKEN_INVALID, "Токен не указан");
            return;
        }
        try {
            Claims claims = jwtService.validateAccessToken(token);
            UUID userId = JwtService.getUserIdFromClaims(claims);
            String email = claims.get("email", String.class);
            Role role = JwtService.getRoleFromClaims(claims);
            UserPrincipal principal = new UserPrincipal(userId, email != null ? email : "", role);
            SecurityContextHolder.getContext().setAuthentication(principal);
            filterChain.doFilter(request, response);
        } catch (JwtService.TokenExpiredException e) {
            sendTokenError(response, ApiError.ErrorCodeEnum.TOKEN_EXPIRED, "Срок действия токена истёк");
        } catch (JwtService.TokenInvalidException e) {
            sendTokenError(response, ApiError.ErrorCodeEnum.TOKEN_INVALID, "Невалидный токен");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) return false;
        return path.startsWith("/auth")
                || path.startsWith("/health")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    private void sendTokenError(HttpServletResponse response, ApiError.ErrorCodeEnum code, String message) throws IOException {
        ApiError error = new ApiError();
        error.setErrorCode(code);
        error.setMessage(message);
        error.setDetails(org.openapitools.jackson.nullable.JsonNullable.undefined());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
