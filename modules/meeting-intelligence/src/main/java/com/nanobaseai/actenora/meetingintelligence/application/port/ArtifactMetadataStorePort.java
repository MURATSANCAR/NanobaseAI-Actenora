package com.nanobaseai.actenora.meetingintelligence.application.port;

import com.nanobaseai.actenora.meetingintelligence.domain.artifact.ArtifactMetadata;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArtifactMetadataStorePort {

    ArtifactMetadata save(ArtifactMetadata metadata);

    Optional<ArtifactMetadata> findByKey(TenantId tenantId, String storageKey);

    List<ArtifactMetadata> findByOccurrence(TenantId tenantId, UUID meetingOccurrenceId);
}
