package com.nanobaseai.actenora.microsoftconnection.application.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Entra directory user projection resolved via Graph {@code /users/{id}}.
 */
public record DirectoryUser(
        String id,
        String displayName,
        String mail,
        String userPrincipalName
) {
    public DirectoryUser {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("directory user id must not be blank");
        }
    }

    public Optional<String> mailOptional() {
        return Optional.ofNullable(mail).filter(s -> !s.isBlank());
    }

    public Optional<String> userPrincipalNameOptional() {
        return Optional.ofNullable(userPrincipalName).filter(s -> !s.isBlank());
    }

    /** Prefer primary mail, fall back to UPN when it looks like an email. */
    public Optional<String> preferredEmail() {
        if (mailOptional().isPresent()) {
            return mailOptional();
        }
        return userPrincipalNameOptional().filter(upn -> upn.contains("@"));
    }
}
