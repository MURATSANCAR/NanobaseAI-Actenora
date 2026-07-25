package com.nanobaseai.actenora.identity.domain;

import java.util.UUID;

public final class AuthorizationDeniedException extends RuntimeException {

    private final UUID userId;
    private final String permission;

    public AuthorizationDeniedException(UUID userId, String permission) {
        super("User " + userId + " lacks permission " + permission);
        this.userId = userId;
        this.permission = permission;
    }

    public UUID userId() { return userId; }
    public String permission() { return permission; }
}
