package com.nanobaseai.actenora.microsoftconnection.application.model;

import java.util.Objects;

/**
 * Result of a Graph sendMail call.
 */
public record MailSendResult(boolean accepted, String providerMessageId) {

    public MailSendResult {
        Objects.requireNonNull(providerMessageId, "providerMessageId");
    }

    public static MailSendResult accepted(String providerMessageId) {
        return new MailSendResult(true, providerMessageId);
    }
}
