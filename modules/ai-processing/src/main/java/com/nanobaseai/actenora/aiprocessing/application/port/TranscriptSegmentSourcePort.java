package com.nanobaseai.actenora.aiprocessing.application.port;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Reads transcript segments for an extraction job. Content must never be logged.
 */
public interface TranscriptSegmentSourcePort {

    List<SegmentInput> segmentsFor(TenantId tenantId, UUID transcriptId);

    static TranscriptSegmentSourcePort empty() {
        return (tenantId, transcriptId) -> List.of();
    }
}
