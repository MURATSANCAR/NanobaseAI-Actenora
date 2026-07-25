package com.nanobaseai.actenora.microsoftconnection.application.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Graph sendMail request.
 */
public record MailSendRequest(
        String mailboxUserId,
        String subject,
        String bodyHtml,
        List<String> toRecipients,
        String idempotencyKey
) {

    public MailSendRequest {
        Objects.requireNonNull(mailboxUserId, "mailboxUserId");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(bodyHtml, "bodyHtml");
        Objects.requireNonNull(toRecipients, "toRecipients");
        toRecipients = List.copyOf(toRecipients);
        if (mailboxUserId.isBlank()) {
            throw new IllegalArgumentException("mailboxUserId must not be blank");
        }
        if (toRecipients.isEmpty()) {
            throw new IllegalArgumentException("toRecipients must not be empty");
        }
    }

    public static MailSendRequest of(
            String mailboxUserId,
            String subject,
            String bodyHtml,
            List<String> toRecipients
    ) {
        return new MailSendRequest(mailboxUserId, subject, bodyHtml, toRecipients, UUID.randomUUID().toString());
    }
}
