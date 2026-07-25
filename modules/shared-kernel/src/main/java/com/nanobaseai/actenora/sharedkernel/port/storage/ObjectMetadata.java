package com.nanobaseai.actenora.sharedkernel.port.storage;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Object metadata including retention hints for later deletion jobs.
 */
public record ObjectMetadata(
        String key,
        long sizeBytes,
        String contentType,
        String etag,
        Instant lastModified,
        Map<String, String> userMetadata,
        Instant retentionUntil
) {

    public ObjectMetadata {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(contentType, "contentType");
        userMetadata = Collections.unmodifiableMap(new LinkedHashMap<>(
                userMetadata == null ? Map.of() : userMetadata));
    }

    public Optional<Instant> retentionUntilOptional() {
        return Optional.ofNullable(retentionUntil);
    }

    public Optional<String> userMeta(String name) {
        return Optional.ofNullable(userMetadata.get(name));
    }
}
