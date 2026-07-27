package com.nanobaseai.actenora.aiprocessing.infrastructure.persistence;

import com.nanobaseai.actenora.aiprocessing.application.port.ProcessingArtifactRepository;
import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingArtifact;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryProcessingArtifactRepository implements ProcessingArtifactRepository {

    private final Map<UUID, ProcessingArtifact> store = new ConcurrentHashMap<>();

    @Override
    public void save(ProcessingArtifact artifact) {
        store.put(artifact.id(), artifact);
    }

    @Override
    public List<ProcessingArtifact> findByJobId(UUID jobId) {
        return store.values().stream()
                .filter(a -> a.jobId().equals(jobId))
                .sorted(Comparator.comparing(ProcessingArtifact::createdAt))
                .toList();
    }

    @Override
    public Optional<ProcessingArtifact> findLatestByMeetingAndType(
            UUID tenantId,
            UUID meetingOccurrenceId,
            String artifactType
    ) {
        return store.values().stream()
                .filter(a -> a.tenantId().equals(tenantId))
                .filter(a -> a.meetingOccurrenceId().equals(meetingOccurrenceId))
                .filter(a -> a.artifactType().equals(artifactType))
                .max(Comparator.comparing(ProcessingArtifact::createdAt));
    }

    @Override
    public List<ProcessingArtifact> findByParentMeetingAndType(
            UUID tenantId,
            UUID meetingOccurrenceId,
            String artifactType
    ) {
        List<ProcessingArtifact> out = new ArrayList<>();
        for (ProcessingArtifact a : store.values()) {
            if (a.tenantId().equals(tenantId)
                    && a.meetingOccurrenceId().equals(meetingOccurrenceId)
                    && a.artifactType().equals(artifactType)) {
                out.add(a);
            }
        }
        out.sort(Comparator.comparing(ProcessingArtifact::createdAt));
        return out;
    }

    public void clear() {
        store.clear();
    }
}
