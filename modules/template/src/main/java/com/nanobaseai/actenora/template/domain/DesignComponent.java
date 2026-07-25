package com.nanobaseai.actenora.template.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One drag/drop node in the design canvas. Order defines vertical layout.
 */
public final class DesignComponent {

    private final UUID id;
    private final TemplateComponentType type;
    private final int order;
    private final Map<String, String> props;

    public DesignComponent(UUID id, TemplateComponentType type, int order, Map<String, String> props) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        if (order < 0) {
            throw new TemplateDomainException("INVALID_DESIGN", "Component order must be >= 0");
        }
        this.order = order;
        Map<String, String> copy = new LinkedHashMap<>();
        if (props != null) {
            props.forEach((k, v) -> copy.put(k, v == null ? "" : v));
        }
        this.props = Collections.unmodifiableMap(copy);
    }

    public UUID id() {
        return id;
    }

    public TemplateComponentType type() {
        return type;
    }

    public int order() {
        return order;
    }

    public Map<String, String> props() {
        return props;
    }
}
