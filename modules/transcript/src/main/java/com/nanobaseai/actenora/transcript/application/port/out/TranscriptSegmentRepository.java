package com.nanobaseai.actenora.transcript.application.port.out;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.transcript.api.TranscriptId;
import com.nanobaseai.actenora.transcript.domain.TranscriptSegment;

import java.util.List;

public interface TranscriptSegmentRepository {

    void replaceAll(TenantId tenantId, TranscriptId transcriptId, List<TranscriptSegment> segments);

    List<TranscriptSegment> findByTranscript(TenantId tenantId, TranscriptId transcriptId);

    List<TranscriptSegment> searchByTranscript(
            TenantId tenantId,
            TranscriptId transcriptId,
            String query,
            int limit
    );

    void deleteByTranscript(TenantId tenantId, TranscriptId transcriptId);
}
