package com.example.marketplace.catalog.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Оборачивает запрос в XUserIdRequestWrapper, чтобы X-User-Id подставлялся из JWT (SecurityContext).
 * Должен выполняться после JwtAuthFilter.
 */
public class XUserIdFromJwtFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(new XUserIdRequestWrapper(request), response);
    }
}
