package com.example.marketplace.catalog;

import com.fasterxml.jackson.databind.Module;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SpringBootApplication
@ComponentScan(
    basePackages = "com.example.marketplace.catalog",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = com.example.marketplace.catalog.OpenApiGeneratorApplication.class
    )
)
public class CatalogServiceApplication {

    @Bean
    public Module jsonNullableModule() {
        return new JsonNullableModule();
    }

    public static void main(String[] args) {
        loadEnvFile();
        SpringApplication.run(CatalogServiceApplication.class, args);
    }

    /**
     * Загружает .env в системные свойства для локального запуска (mvn spring-boot:run / IDE).
     * В Docker переменные уже заданы через env_file, перезапись не делаем.
     */
    private static void loadEnvFile() {
        List<Path> candidates = List.of(
            Path.of(System.getProperty("user.dir"), ".env"),
            Path.of(System.getProperty("user.dir"), "..", ".env"),
            Path.of(System.getProperty("user.dir"), "services", "catalog-service", ".env")
        );
        for (Path path : candidates) {
            Path normalized = path.normalize();
            if (Files.isRegularFile(normalized)) {
                try {
                    Files.readAllLines(normalized).stream()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .forEach(line -> {
                            int eq = line.indexOf('=');
                            if (eq > 0) {
                                String key = line.substring(0, eq).trim();
                                String value = line.substring(eq + 1).trim();
                                if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                                    value = value.substring(1, value.length() - 1);
                                }
                                if (System.getenv(key) == null) {
                                    System.setProperty(key, value);
                                }
                            }
                        });
                    break;
                } catch (Exception ignored) {
                }
            }
        }
    }
}
