package com.nanobaseai.actenora.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Explicit CORS origin allowlist (deny-by-default when empty in prod).
 * Operator identity headers are only allowed when {@code actenora.security.auth.mode=headers}.
 */
@Configuration
public class CorsAllowlistConfig {

    @Bean
    public CorsFilter corsFilter(
            @Value("${actenora.security.cors.allowed-origins:}") String allowedOrigins,
            @Value("${actenora.security.auth.mode:headers}") String authMode
    ) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (origins.isEmpty()) {
            config.setAllowedOrigins(List.of());
        } else {
            config.setAllowedOrigins(origins);
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        List<String> headers = new ArrayList<>(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Actenora-Tenant-Id",
                "X-Request-Id",
                "Idempotency-Key"
        ));
        String mode = authMode == null ? "" : authMode.trim().toLowerCase();
        if ("headers".equals(mode) || "mock".equals(mode)) {
            headers.addAll(List.of(
                    "X-Actenora-Entra-Oid",
                    "X-Actenora-Entra-Tid",
                    "X-Actenora-Email",
                    "X-Actenora-Display-Name",
                    "X-Actenora-Global-Admin"
            ));
        }
        config.setAllowedHeaders(headers);
        config.setExposedHeaders(List.of("X-Request-Id", "Retry-After", "X-Actenora-Composition"));
        config.setAllowCredentials(true);
        config.setMaxAge(600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
