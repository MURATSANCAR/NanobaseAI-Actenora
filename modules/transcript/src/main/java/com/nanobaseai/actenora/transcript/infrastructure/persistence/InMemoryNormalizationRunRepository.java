package com.nanobaseai.actenora.transcript.infrastructure.persistence;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.transcript.api.TranscriptId;
import com.nanobaseai.actenora.transcript.application.port.out.NormalizationRunRepository;
import com.nanobaseai.actenora.transcript.domain.normalization.NormalizationRun;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryNormalizationRunRepository implements NormalizationRunRepository {

    private final Map<UUID, NormalizationRun> byId = new ConcurrentHashMap<>();
    private final Map<String, UUID> byVersionKey = new ConcurrentHashMap<>();

    @Override
    public NormalizationRun save(NormalizationRun run) {
        byId.put(run.id(), run);
        byVersionKey.put(versionKey(run.tenantId(), run.transcriptId(), run.normalizationVersion()), run.id());
        return run;
    }

    @Override
    public Optional<NormalizationRun> findById(TenantId tenantId, UUID runId) {
        return Optional.ofNullable(byId.get(runId))
                .filter(r -> r.tenantId().equals(tenantId));
    }

    @Override
    public Optional<NormalizationRun> findByTranscriptAndVersion(
            TenantId tenantId, TranscriptId transcriptId, String normalizationVersion) {
        UUID id = byVersionKey.get(versionKey(tenantId, transcriptId, normalizationVersion));
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<NormalizationRun> findSucceededByTranscriptAndVersion(
            TenantId tenantId, TranscriptId transcriptId, String normalizationVersion) {
        return findIdempotent(tenantId, transcriptId, normalizationVersion);
    }

    private static String versionKey(TenantId tenantId, TranscriptId transcriptId, String version) {
        return tenantId.value() + "|" + transcriptId.value() + "|" + version;
    }
}
