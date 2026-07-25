package com.nanobaseai.actenora.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * FAZ 27 — explicit CORS origin allowlist (deny-by-default when empty in prod).
 */
@Configuration
public class CorsAllowlistConfig {

    @Bean
    public CorsFilter corsFilter(
            @Value("${actenora.security.cors.allowed-origins:}") String allowedOrigins
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
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Actenora-Tenant-Id",
                "X-Request-Id",
                "Idempotency-Key",
                "X-Mock-Entra-Oid",
                "X-Mock-Entra-Tid",
                "X-Mock-Email",
                "X-Mock-Display-Name",
                "X-Mock-Global-Admin"));
        config.setExposedHeaders(List.of("X-Request-Id", "Retry-After"));
        config.setAllowCredentials(true);
        config.setMaxAge(600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
