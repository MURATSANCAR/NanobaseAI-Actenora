package com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence;

import com.nanobaseai.actenora.meetingintelligence.application.port.ArtifactMetadataStorePort;
import com.nanobaseai.actenora.meetingintelligence.domain.artifact.ArtifactMetadata;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryArtifactMetadataStore implements ArtifactMetadataStorePort {

    private final Map<String, ArtifactMetadata> byKey = new ConcurrentHashMap<>();

    @Override
    public ArtifactMetadata save(ArtifactMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        byKey.put(key(metadata.tenantId(), metadata.storageKey()), metadata);
        return metadata;
    }

    @Override
    public Optional<ArtifactMetadata> findByKey(TenantId tenantId, String storageKey) {
        return Optional.ofNullable(byKey.get(key(tenantId, storageKey)));
    }

    @Override
    public List<ArtifactMetadata> findByOccurrence(TenantId tenantId, UUID meetingOccurrenceId) {
        return byKey.values().stream()
                .filter(item -> item.tenantId().equals(tenantId))
                .filter(item -> item.meetingOccurrenceId().filter(meetingOccurrenceId::equals).isPresent())
                .toList();
    }

    private static String key(TenantId tenantId, String storageKey) {
        return tenantId.value() + ":" + storageKey;
    }
}
