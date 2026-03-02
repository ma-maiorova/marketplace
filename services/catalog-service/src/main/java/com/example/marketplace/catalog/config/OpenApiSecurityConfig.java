package com.example.marketplace.catalog.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Добавляет в OpenAPI схему Bearer JWT, чтобы в Swagger UI была кнопка «Authorize»
 * и запросы к защищённым эндпоинтам отправлялись с заголовком Authorization.
 */
@Configuration
public class OpenApiSecurityConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI openApiWithSecurity() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Access token из POST /auth/login или /auth/refresh")))
                .security(List.of(new SecurityRequirement().addList(BEARER_AUTH)));
    }
}
