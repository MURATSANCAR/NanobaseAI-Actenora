package com.nanobaseai.actenora.identity.domain;

import java.util.UUID;

public final class UserNotActiveException extends RuntimeException {

    private final UUID userId;
    private final UserStatus status;

    public UserNotActiveException(UUID userId, UserStatus status) {
        super("User " + userId + " is not active (status=" + status + ")");
        this.userId = userId;
        this.status = status;
    }

    public UUID userId() { return userId; }
    public UserStatus status() { return status; }
}
