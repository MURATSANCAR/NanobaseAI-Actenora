package com.nanobaseai.actenora.transcript.infrastructure.persistence;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.transcript.api.TranscriptId;
import com.nanobaseai.actenora.transcript.application.port.out.TranscriptSegmentRepository;
import com.nanobaseai.actenora.transcript.domain.TranscriptSegment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryTranscriptSegmentRepository implements TranscriptSegmentRepository {

    private final Map<String, List<TranscriptSegment>> byTranscript = new ConcurrentHashMap<>();

    @Override
    public void replaceAll(TenantId tenantId, TranscriptId transcriptId, List<TranscriptSegment> segments) {
        byTranscript.put(key(tenantId, transcriptId), List.copyOf(segments));
    }

    @Override
    public List<TranscriptSegment> findByTranscript(TenantId tenantId, TranscriptId transcriptId) {
        return new ArrayList<>(byTranscript.getOrDefault(key(tenantId, transcriptId), List.of()));
    }

    @Override
    public void deleteByTranscript(TenantId tenantId, TranscriptId transcriptId) {
        byTranscript.remove(key(tenantId, transcriptId));
    }

    private static String key(TenantId tenantId, TranscriptId transcriptId) {
        return tenantId.value() + "|" + transcriptId.value();
    }
}
