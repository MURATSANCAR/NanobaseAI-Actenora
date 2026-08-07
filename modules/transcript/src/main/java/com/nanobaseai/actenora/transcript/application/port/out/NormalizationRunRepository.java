package com.nanobaseai.actenora.transcript.application.port.out;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.transcript.api.TranscriptId;
import com.nanobaseai.actenora.transcript.domain.normalization.NormalizationRun;
import com.nanobaseai.actenora.transcript.domain.normalization.NormalizationRunStatus;

import java.util.Optional;
import java.util.UUID;

public interface NormalizationRunRepository {

    NormalizationRun save(NormalizationRun run);

    Optional<NormalizationRun> findById(TenantId tenantId, UUID runId);

    Optional<NormalizationRun> findByTranscriptAndVersion(
            TenantId tenantId, TranscriptId transcriptId, String normalizationVersion);

    Optional<NormalizationRun> findSucceededByTranscriptAndVersion(
            TenantId tenantId, TranscriptId transcriptId, String normalizationVersion);

    default Optional<NormalizationRun> findIdempotent(
            TenantId tenantId, TranscriptId transcriptId, String normalizationVersion) {
        return findByTranscriptAndVersion(tenantId, transcriptId, normalizationVersion)
                .filter(run -> run.status() == NormalizationRunStatus.SUCCEEDED);
    }
}
