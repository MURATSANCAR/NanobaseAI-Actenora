package com.nanobaseai.actenora.template.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Versioned drag/drop design document. Arbitrary JavaScript is forbidden;
 * only allow-listed components and string props are accepted.
 */
public final class DesignSchema {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    private final int schemaVersion;
    private final String pageSize;
    private final List<DesignComponent> components;

    public DesignSchema(int schemaVersion, String pageSize, List<DesignComponent> components) {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new TemplateDomainException("INVALID_DESIGN", "Unsupported design schema version: " + schemaVersion);
        }
        this.schemaVersion = schemaVersion;
        this.pageSize = pageSize == null || pageSize.isBlank() ? "A4" : pageSize.trim();
        List<DesignComponent> sorted = new ArrayList<>(Objects.requireNonNull(components, "components"));
        sorted.sort(Comparator.comparingInt(DesignComponent::order));
        this.components = Collections.unmodifiableList(sorted);
        DesignSchemaValidator.validate(this);
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public String pageSize() {
        return pageSize;
    }

    public List<DesignComponent> components() {
        return components;
    }
}
