package com.nanobaseai.actenora.security.auth;

/**
 * Authentication mode for the platform edge.
 * {@code HEADERS} is local/dev operator identity via {@code X-Actenora-*} headers (real Entra values).
 * {@code ENTRA} is JWT resource server for staging/production SPA MSAL.
 */
public enum AuthMode {
    ENTRA,
    HEADERS;

    public static AuthMode fromConfig(String mode) {
        if (mode == null || mode.isBlank()) {
            return HEADERS;
        }
        return switch (mode.trim().toLowerCase()) {
            case "entra" -> ENTRA;
            // "mock" retained as config alias only — means header IdP, not fake data
            case "headers", "mock" -> HEADERS;
            default -> throw new IllegalArgumentException(
                    "Unknown actenora.security.auth.mode '" + mode + "' (expected entra|headers)");
        };
    }
}
