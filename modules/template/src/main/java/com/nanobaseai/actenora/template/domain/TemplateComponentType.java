package com.nanobaseai.actenora.template.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * Allowed Template Studio building blocks. Drag/drop schema may only reference these.
 */
public enum TemplateComponentType {
    LOGO,
    HEADER,
    METADATA,
    PARTICIPANT_TABLE,
    EXECUTIVE_SUMMARY,
    AGENDA,
    DECISIONS,
    ACTIONS,
    RISKS,
    OPEN_QUESTIONS,
    COMMITMENTS,
    SIGNATURE,
    FOOTER,
    CONFIDENTIALITY,
    PAGE_NUMBER;

    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<TemplateComponentType> fromWire(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
        try {
            return Optional.of(TemplateComponentType.valueOf(normalized));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
