package com.nanobaseai.actenora.aiprocessing.application.port;

import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingArtifact;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcessingArtifactRepository {

    void save(ProcessingArtifact artifact);

    List<ProcessingArtifact> findByJobId(UUID jobId);

    Optional<ProcessingArtifact> findLatestByMeetingAndType(
            UUID tenantId,
            UUID meetingOccurrenceId,
            String artifactType
    );

    List<ProcessingArtifact> findByParentMeetingAndType(
            UUID tenantId,
            UUID meetingOccurrenceId,
            String artifactType
    );
}
