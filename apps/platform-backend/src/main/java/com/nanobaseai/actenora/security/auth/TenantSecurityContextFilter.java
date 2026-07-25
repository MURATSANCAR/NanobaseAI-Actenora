package com.nanobaseai.actenora.security.auth;

import com.nanobaseai.actenora.sharedkernel.security.AuthenticatedPrincipal;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves authenticated identity into {@link TenantSecurityContext}.
 * Tenant id is never taken from body/query — only from IdP tid → tenant mapping.
 */
public final class TenantSecurityContextFilter extends OncePerRequestFilter {

    private final AuthenticationBindingService bindingService;
    private final AuthMode authMode;

    public TenantSecurityContextFilter(AuthenticationBindingService bindingService, AuthMode authMode) {
        this.bindingService = Objects.requireNonNull(bindingService, "bindingService");
        this.authMode = Objects.requireNonNull(authMode, "authMode");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            Optional<AuthenticatedPrincipal> principal = resolve(request);
            if (principal.isPresent()) {
                TenantSecurityContext.set(principal.get());
            } else if (requiresAuthentication(request)) {
                writeUnauthorized(response, request.getRequestURI(), "Missing or invalid identity claims");
                return;
            }
            filterChain.doFilter(request, response);
        } catch (TenantSecurityContextFilterException ex) {
            writeProblem(response, ex.status(), ex.title(), ex.detail(), ex.code(), request.getRequestURI());
        } catch (RuntimeException ex) {
            if (isAuthFailure(ex)) {
                writeProblem(response, 403, "Forbidden", ex.getMessage(), "AUTH_BINDING_FAILED", request.getRequestURI());
                return;
            }
            throw ex;
        } finally {
            TenantSecurityContext.clear();
        }
    }

    private Optional<AuthenticatedPrincipal> resolve(HttpServletRequest request) {
        Map<String, Object> jwtClaims = Map.of();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            jwtClaims = jwt.getClaims();
        }
        Map<String, String> headers = authMode == AuthMode.MOCK ? extractHeaders(request) : Map.of();
        try {
            return bindingService.bind(jwtClaims, headers);
        } catch (IllegalArgumentException ex) {
            if (authMode == AuthMode.MOCK && jwtClaims.isEmpty() && headers.isEmpty()) {
                return Optional.empty();
            }
            throw new TenantSecurityContextFilterException(401, "Unauthorized", ex.getMessage(), "INVALID_CLAIMS");
        }
    }

    private static Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return Map.of();
        }
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return Collections.unmodifiableMap(headers);
    }

    private static boolean requiresAuthentication(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith("/api/");
    }

    private static boolean isAuthFailure(RuntimeException ex) {
        String name = ex.getClass().getName();
        return name.contains("TenantNotActive")
                || name.contains("UserNotActive")
                || name.contains("AuthorizationDenied")
                || name.contains("DuplicateEntra");
    }

    private static void writeUnauthorized(HttpServletResponse response, String instance, String detail)
            throws IOException {
        writeProblem(response, 401, "Unauthorized", detail, "UNAUTHORIZED", instance);
    }

    private static void writeProblem(
            HttpServletResponse response,
            int status,
            String title,
            String detail,
            String code,
            String instance
    ) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        String json = "{"
                + "\"type\":\"https://actenora.nanobase.ai/problems/" + code.toLowerCase().replace('_', '-') + "\","
                + "\"title\":\"" + escape(title) + "\","
                + "\"status\":" + status + ","
                + "\"detail\":\"" + escape(detail == null ? "" : detail) + "\","
                + "\"instance\":\"" + escape(URI.create(instance == null ? "/" : instance).toString()) + "\","
                + "\"code\":\"" + escape(code) + "\""
                + "}";
        response.getWriter().write(json);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class TenantSecurityContextFilterException extends RuntimeException {
        private final int status;
        private final String title;
        private final String detail;
        private final String code;

        private TenantSecurityContextFilterException(int status, String title, String detail, String code) {
            super(detail);
            this.status = status;
            this.title = title;
            this.detail = detail;
            this.code = code;
        }

        int status() { return status; }
        String title() { return title; }
        String detail() { return detail; }
        String code() { return code; }
    }
}
