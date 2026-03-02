package com.example.marketplace.catalog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Логирует каждый API-запрос в формате JSON.
 * Поля: request_id, method, endpoint, status_code, duration_ms, user_id, timestamp;
 * для POST/PUT/DELETE — request_body (чувствительные данные маскируются).
 * Пробрасывает request_id в заголовке X-Request-Id ответа.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_ATTR = "request_id";
    private static final String X_REQUEST_ID = "X-Request-Id";
    private static final String X_USER_ID = "X-User-Id";

    private final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final ObjectMapper objectMapper;

    public RequestLoggingFilter() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(X_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        request.setAttribute(REQUEST_ID_ATTR, requestId);
        response.setHeader(X_REQUEST_ID, requestId);

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            wrappedResponse.copyBodyToResponse();
            logRequest(wrappedRequest, wrappedResponse, requestId, startNanos);
        }
    }

    private void logRequest(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response,
                            String requestId, long startNanos) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        String method = request.getMethod();
        String endpoint = request.getRequestURI();
        if (request.getQueryString() != null) {
            endpoint = endpoint + "?" + request.getQueryString();
        }
        int statusCode = response.getStatus();
        String userId = request.getHeader(X_USER_ID);
        if (userId == null || userId.isBlank()) {
            userId = null;
        }
        String timestamp = Instant.now().toString();

        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("request_id", requestId);
        logEntry.put("method", method);
        logEntry.put("endpoint", endpoint);
        logEntry.put("status_code", statusCode);
        logEntry.put("duration_ms", durationMs);
        logEntry.put("user_id", userId);
        logEntry.put("timestamp", timestamp);

        if (isMutating(method)) {
            String body = getRequestBody(request);
            if (body != null && !body.isEmpty()) {
                logEntry.put("request_body", RequestBodyMasker.mask(body));
            }
        }

        try {
            String json = objectMapper.writeValueAsString(logEntry);
            log.info(json);
        } catch (Exception e) {
            log.warn("Failed to serialize request log: {}", e.getMessage());
        }
    }

    private static boolean isMutating(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);
    }

    private static String getRequestBody(ContentCachingRequestWrapper request) {
        byte[] buf = request.getContentAsByteArray();
        if (buf == null || buf.length == 0) {
            return null;
        }
        return new String(buf, StandardCharsets.UTF_8);
    }
}
