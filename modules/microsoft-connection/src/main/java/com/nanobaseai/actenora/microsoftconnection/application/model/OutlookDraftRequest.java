package com.nanobaseai.actenora.microsoftconnection.application.model;

import java.util.List;
import java.util.Objects;

/**
 * A user-reviewable Outlook message draft.
 */
public record OutlookDraftRequest(
        String mailboxUserId,
        String subject,
        String bodyHtml,
        List<String> toRecipients,
        String idempotencyKey
) {

    public OutlookDraftRequest {
        mailboxUserId = requireText(mailboxUserId, "mailboxUserId");
        subject = requireText(subject, "subject");
        bodyHtml = requireText(bodyHtml, "bodyHtml");
        toRecipients = List.copyOf(Objects.requireNonNull(toRecipients, "toRecipients")).stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
