package com.nanobaseai.actenora.security.auth;

import com.nanobaseai.actenora.identity.api.IdentityApi;
import com.nanobaseai.actenora.identity.application.port.IdentityProviderPort;
import com.nanobaseai.actenora.identity.infrastructure.provider.EntraIdentityProvider;
import com.nanobaseai.actenora.identity.infrastructure.provider.MockIdentityProvider;
import com.nanobaseai.actenora.tenant.api.TenantApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Arrays;
import java.util.Locale;

@Configuration
@EnableWebSecurity
public class PlatformSecurityConfiguration {

    @Bean
    AuthMode authMode(
            @Value("${actenora.security.auth.mode:mock}") String mode,
            Environment environment
    ) {
        AuthMode authMode = AuthMode.valueOf(mode.trim().toUpperCase(Locale.ROOT));
        boolean production = Arrays.stream(environment.getActiveProfiles())
                .map(p -> p.toLowerCase(Locale.ROOT))
                .anyMatch(p -> p.equals("prod") || p.equals("production"));
        if (production && authMode == AuthMode.MOCK) {
            throw new IllegalStateException(
                    "Refusing to start: actenora.security.auth.mode=mock is forbidden on production profiles");
        }
        return authMode;
    }

    @Bean
    IdentityProviderPort identityProviderPort(AuthMode authMode) {
        return switch (authMode) {
            case ENTRA -> new EntraIdentityProvider();
            case MOCK -> new MockIdentityProvider();
        };
    }

    @Bean
    AuthenticationBindingService authenticationBindingService(
            IdentityProviderPort identityProviderPort,
            TenantApi tenantApi,
            IdentityApi identityApi
    ) {
        return new AuthenticationBindingService(identityProviderPort, tenantApi, identityApi);
    }

    @Bean
    @Order(1)
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            AuthenticationBindingService bindingService,
            AuthMode authMode
    ) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        TenantSecurityContextFilter tenantFilter = new TenantSecurityContextFilter(bindingService, authMode);

        if (authMode == AuthMode.ENTRA) {
            http.authorizeHttpRequests(auth -> auth
                            .requestMatchers(
                                    "/actuator/health",
                                    "/actuator/health/**",
                                    "/actuator/info"
                            ).permitAll()
                            .requestMatchers("/api/**").authenticated()
                            .anyRequest().permitAll())
                    .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
            http.addFilterAfter(tenantFilter, BearerTokenAuthenticationFilter.class);
        } else {
            // Mock IdP: Spring Security permits all; TenantSecurityContextFilter enforces /api/**
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            http.addFilterBefore(tenantFilter, UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }
}
