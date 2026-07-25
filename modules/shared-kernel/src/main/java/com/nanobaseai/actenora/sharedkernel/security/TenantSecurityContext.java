package com.nanobaseai.actenora.sharedkernel.security;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Request-scoped authenticated principal. Never trust body/query tenant IDs —
 * resolve tenant only from this context after identity binding.
 */
public final class TenantSecurityContext {

    private static final ThreadLocal<AuthenticatedPrincipal> CURRENT = new ThreadLocal<>();

    private TenantSecurityContext() {
    }

    public static void set(AuthenticatedPrincipal principal) {
        CURRENT.set(Objects.requireNonNull(principal, "principal"));
    }

    public static Optional<AuthenticatedPrincipal> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static AuthenticatedPrincipal require() {
        AuthenticatedPrincipal principal = CURRENT.get();
        if (principal == null) {
            throw new IllegalStateException("No authenticated principal bound to this thread");
        }
        return principal;
    }

    public static TenantId requireTenantId() {
        return require().tenantId();
    }

    public static UUID requireUserId() {
        return require().userId();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static Scope open(AuthenticatedPrincipal principal) {
        AuthenticatedPrincipal previous = CURRENT.get();
        CURRENT.set(Objects.requireNonNull(principal, "principal"));
        return new Scope(previous);
    }

    public static final class Scope implements AutoCloseable {
        private final AuthenticatedPrincipal previous;

        private Scope(AuthenticatedPrincipal previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
