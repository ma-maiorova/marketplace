package com.example.marketplace.catalog.config;

import com.example.marketplace.catalog.auth.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Обёртка запроса: для заголовка X-User-Id возвращает userId из JWT (SecurityContext),
 * чтобы сгенерированные API-методы получали пользователя из токена, а не из заголовка.
 */
public class XUserIdRequestWrapper extends HttpServletRequestWrapper {

    private static final String X_USER_ID = "X-User-Id";

    public XUserIdRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public String getHeader(String name) {
        if (X_USER_ID.equalsIgnoreCase(name)) {
            String fromJwt = getUserIdFromJwt();
            if (fromJwt != null) {
                return fromJwt;
            }
        }
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        if (X_USER_ID.equalsIgnoreCase(name)) {
            String fromJwt = getUserIdFromJwt();
            if (fromJwt != null) {
                return Collections.enumeration(List.of(fromJwt));
            }
        }
        return super.getHeaders(name);
    }

    private static String getUserIdFromJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getUserId().toString();
        }
        return null;
    }
}
