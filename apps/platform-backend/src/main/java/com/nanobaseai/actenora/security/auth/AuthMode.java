package com.nanobaseai.actenora.security.auth;

/**
 * Authentication mode for the platform edge.
 * {@code MOCK} is local/dev only and refused on production profiles.
 */
public enum AuthMode {
    ENTRA,
    MOCK
}
