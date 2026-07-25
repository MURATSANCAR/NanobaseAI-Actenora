package com.nanobaseai.actenora.delivery.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * One destination for a note version. External recipients never share a message with others.
 */
public record DeliveryRecipient(
        UUID id,
        String email,
        RecipientKind kind,
        String displayName
) {

    public DeliveryRecipient {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(kind, "kind");
        email = normalizeEmail(email);
        if (email.isBlank() || !email.contains("@")) {
            throw new DeliveryDomainException("INVALID_RECIPIENT", "recipient email is invalid");
        }
        if (displayName == null) {
            displayName = "";
        }
    }

    public static DeliveryRecipient of(String email, RecipientKind kind, String displayName) {
        return new DeliveryRecipient(UUID.randomUUID(), email, kind, displayName);
    }

    public static DeliveryRecipient external(String email, String displayName) {
        return of(email, RecipientKind.EXTERNAL, displayName);
    }

    public static DeliveryRecipient internal(String email, String displayName) {
        return of(email, RecipientKind.INTERNAL, displayName);
    }

    public boolean isExternal() {
        return kind == RecipientKind.EXTERNAL;
    }

    public String idempotencyKey(UUID noteVersionId) {
        return noteVersionId + "|" + email;
    }

    private static String normalizeEmail(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
