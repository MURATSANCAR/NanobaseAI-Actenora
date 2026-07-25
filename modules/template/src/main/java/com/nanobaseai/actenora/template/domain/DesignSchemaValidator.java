package com.nanobaseai.actenora.template.domain;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Ensures design schemas remain declarative: no arbitrary JS, event handlers, or unknown components.
 */
public final class DesignSchemaValidator {

    private static final Pattern EVENT_HANDLER = Pattern.compile("^on[a-z0-9_]+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCRIPTISH = Pattern.compile(
            "(?i)(<script|javascript:|expression\\s*\\(|eval\\s*\\(|Function\\s*\\()");

    private DesignSchemaValidator() {
    }

    public static void validate(DesignSchema schema) {
        if (schema.components().isEmpty()) {
            throw new TemplateDomainException("INVALID_DESIGN", "Design must contain at least one component");
        }
        Set<UUID> ids = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (DesignComponent component : schema.components()) {
            if (!ids.add(component.id())) {
                throw new TemplateDomainException("INVALID_DESIGN", "Duplicate component id: " + component.id());
            }
            if (!orders.add(component.order())) {
                throw new TemplateDomainException("INVALID_DESIGN", "Duplicate component order: " + component.order());
            }
            validateProps(component);
        }
    }

    private static void validateProps(DesignComponent component) {
        for (var entry : component.props().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isBlank()) {
                throw new TemplateDomainException("INVALID_DESIGN", "Prop key must not be blank");
            }
            String normalized = key.trim().toLowerCase(Locale.ROOT);
            if (EVENT_HANDLER.matcher(normalized).matches()
                    || normalized.equals("script")
                    || normalized.contains("javascript")) {
                throw new TemplateDomainException(
                        "ARBITRARY_JS_FORBIDDEN",
                        "Prop '" + key + "' is not allowed (no arbitrary JavaScript)");
            }
            if (value != null && SCRIPTISH.matcher(value).find()) {
                throw new TemplateDomainException(
                        "ARBITRARY_JS_FORBIDDEN",
                        "Prop '" + key + "' contains forbidden script content");
            }
        }
    }
}
