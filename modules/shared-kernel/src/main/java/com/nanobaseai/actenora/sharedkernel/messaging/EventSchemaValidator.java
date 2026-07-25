package com.nanobaseai.actenora.sharedkernel.messaging;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Validates envelope structure, supported versions, and payload size.
 */
public final class EventSchemaValidator {

    private final EventMessagingConfig config;

    public EventSchemaValidator(EventMessagingConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public void validateForPublish(EventEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        rejectIfTooLarge(envelope.payloadJson());
        if (!config.supportsVersion(envelope.eventVersion())) {
            throw new ActenoraException(
                    RetryClassifier.Default.CODE_UNSUPPORTED_VERSION,
                    "Unsupported event version: " + envelope.eventVersion());
        }
        validatePayloadShape(envelope.payloadJson());
    }

    public void validateForConsume(EventEnvelope envelope) {
        validateForPublish(envelope);
    }

    public void rejectIfTooLarge(String payloadJson) {
        int bytes = payloadJson.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > config.maxPayloadBytes()) {
            throw new ActenoraException(
                    RetryClassifier.Default.CODE_PAYLOAD_TOO_LARGE,
                    "Payload size " + bytes + " exceeds limit " + config.maxPayloadBytes());
        }
    }

    private void validatePayloadShape(String payloadJson) {
        String trimmed = payloadJson.trim();
        if (trimmed.isEmpty()) {
            throw new ActenoraException(
                    RetryClassifier.Default.CODE_MALFORMED,
                    "Payload must not be empty");
        }
        char first = trimmed.charAt(0);
        if (first != '{' && first != '[') {
            throw new ActenoraException(
                    RetryClassifier.Default.CODE_MALFORMED,
                    "Payload must be JSON object or array");
        }
        // Lightweight balance check — full JSON Schema lives in event-contracts; runtime rejects garbage.
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> inString = true;
                case '{', '[' -> depth++;
                case '}', ']' -> {
                    depth--;
                    if (depth < 0) {
                        throw malformed();
                    }
                }
                default -> {
                }
            }
        }
        if (inString || depth != 0) {
            throw malformed();
        }
    }

    private static ActenoraException malformed() {
        return new ActenoraException(
                RetryClassifier.Default.CODE_MALFORMED,
                "Malformed JSON payload");
    }
}
