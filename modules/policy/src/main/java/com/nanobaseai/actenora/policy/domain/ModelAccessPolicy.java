package com.nanobaseai.actenora.policy.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Model allowlist and critical-meeting fallback permission. */
public record ModelAccessPolicy(
        Set<String> allowedModelKeys,
        boolean criticalMeetingFallbackAllowed
) {
    public ModelAccessPolicy {
        Objects.requireNonNull(allowedModelKeys, "allowedModelKeys");
        allowedModelKeys = Collections.unmodifiableSet(new LinkedHashSet<>(allowedModelKeys));
    }

    public boolean isModelAllowed(String modelKey) {
        Objects.requireNonNull(modelKey, "modelKey");
        return allowedModelKeys.contains(modelKey);
    }

    public static ModelAccessPolicy systemDefaults() {
        return new ModelAccessPolicy(Set.of("local-default"), true);
    }
}
