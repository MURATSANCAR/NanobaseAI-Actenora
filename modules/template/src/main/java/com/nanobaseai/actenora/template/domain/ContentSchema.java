package com.nanobaseai.actenora.template.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Declares which note content keys bind to design components.
 */
public final class ContentSchema {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    private static final Set<String> ALLOWED_KEYS = Set.of(
            "logo", "header", "metadata", "participant_table", "executive_summary",
            "agenda", "decisions", "actions", "risks", "open_questions", "commitments",
            "signature", "footer", "confidentiality", "page_number"
    );

    private final int schemaVersion;
    private final Map<String, String> bindings;

    public ContentSchema(int schemaVersion, Map<String, String> bindings) {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new TemplateDomainException("INVALID_CONTENT_SCHEMA", "Unsupported content schema version: " + schemaVersion);
        }
        this.schemaVersion = schemaVersion;
        Map<String, String> copy = new LinkedHashMap<>();
        Objects.requireNonNull(bindings, "bindings").forEach((k, v) -> {
            if (k == null || k.isBlank()) {
                throw new TemplateDomainException("INVALID_CONTENT_SCHEMA", "Binding key must not be blank");
            }
            String key = k.trim().toLowerCase();
            if (!ALLOWED_KEYS.contains(key)) {
                throw new TemplateDomainException("INVALID_CONTENT_SCHEMA", "Unknown content binding key: " + key);
            }
            if (v == null || v.isBlank()) {
                throw new TemplateDomainException("INVALID_CONTENT_SCHEMA", "Binding source must not be blank for " + key);
            }
            if (containsScript(v)) {
                throw new TemplateDomainException("ARBITRARY_JS_FORBIDDEN", "Content binding must not contain script: " + key);
            }
            copy.put(key, v.trim());
        });
        this.bindings = Collections.unmodifiableMap(copy);
    }

    public static ContentSchema empty() {
        return new ContentSchema(CURRENT_SCHEMA_VERSION, Map.of());
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public Map<String, String> bindings() {
        return bindings;
    }

    private static boolean containsScript(String value) {
        String lower = value.toLowerCase();
        return lower.contains("<script") || lower.contains("javascript:") || lower.contains("onerror=");
    }
}
