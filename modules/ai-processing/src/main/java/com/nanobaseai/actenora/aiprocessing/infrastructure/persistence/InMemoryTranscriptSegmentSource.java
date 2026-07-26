package com.nanobaseai.actenora.aiprocessing.infrastructure.persistence;

import com.nanobaseai.actenora.aiprocessing.application.port.TranscriptSegmentSourcePort;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory transcript segment source for FAZ 14 job-path tests.
 */
public final class InMemoryTranscriptSegmentSource implements TranscriptSegmentSourcePort {

    private final Map<String, List<SegmentInput>> byTranscript = new ConcurrentHashMap<>();

    public void put(TenantId tenantId, UUID transcriptId, List<SegmentInput> segments) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(transcriptId, "transcriptId");
        byTranscript.put(
                key(tenantId, transcriptId),
                List.copyOf(Objects.requireNonNull(segments, "segments")));
    }

    @Override
    public List<SegmentInput> segmentsFor(TenantId tenantId, UUID transcriptId) {
        List<SegmentInput> segments = byTranscript.getOrDefault(key(tenantId, transcriptId), List.of());
        List<SegmentInput> copy = new ArrayList<>(segments);
        copy.sort(Comparator.comparingInt(SegmentInput::sequence));
        return List.copyOf(copy);
    }

    public void clear() {
        byTranscript.clear();
    }

    private static String key(TenantId tenantId, UUID transcriptId) {
        return tenantId.value() + ":" + transcriptId;
    }
}
