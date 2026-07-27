package com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TranscriptChunk;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Segment id → content for post-extract evidence checks.
 */
public final class EvidenceIndex {

    private final Map<String, String> byId;

    public EvidenceIndex(Map<String, String> byId) {
        this.byId = Map.copyOf(Objects.requireNonNull(byId, "byId"));
    }

    public static EvidenceIndex from(List<SegmentInput> segments) {
        Map<String, String> map = new HashMap<>();
        for (SegmentInput segment : segments) {
            map.put(segment.segmentId(), segment.content());
        }
        return new EvidenceIndex(map);
    }

    public static EvidenceIndex from(TranscriptChunk chunk) {
        return from(chunk.segments());
    }

    public String resolve(List<String> evidenceIds) {
        StringBuilder sb = new StringBuilder();
        for (String id : evidenceIds) {
            String content = byId.get(id);
            if (content != null && !content.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(content);
            }
        }
        return sb.toString();
    }

    public boolean allResolved(List<String> evidenceIds) {
        if (evidenceIds == null || evidenceIds.isEmpty()) {
            return false;
        }
        for (String id : evidenceIds) {
            if (!byId.containsKey(id)) {
                return false;
            }
        }
        return true;
    }
}
