package com.nanobaseai.actenora.aiprocessing.domain.pipeline.note;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.CommitmentCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ImportantFactCandidate;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityEvalPackTest {

    @Test
    void buildsPackWithCountsAndItems() {
        FinalNoteDraft draft = new FinalNoteDraft(
                "Özet",
                List.of(new DecisionCandidate("Karar A", List.of("s1"), 0.9)),
                List.of(new ActionItemCandidate("İş B", "Can", null, List.of("s2"), 0.9)),
                List.of(),
                List.of(),
                List.of(new CommitmentCandidate("Planı paylaşacağım.", "Can", List.of("s2"), 0.88)),
                List.of(),
                List.of(),
                List.of(),
                List.of(new ImportantFactCandidate("40 tekrarın 3'ünde 401 görüldü.", List.of("s3"), 0.9)),
                List.of("CONSISTENCY_AUDIT_PASSED"),
                List.of("s1", "s2", "s3"),
                0.92,
                false
        );
        UUID noteId = UUID.randomUUID();
        Map<String, Object> pack = QualityEvalPack.build(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                noteId, "nanobase-qwen", "pv-final-v1", "schema-1", draft, Instant.parse("2026-08-05T00:00:00Z")
        );
        assertEquals(QualityEvalPack.ARTIFACT_TYPE, pack.get("artifactType"));
        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) pack.get("counts");
        assertEquals(1, counts.get("decisions"));
        assertEquals(1, counts.get("actionItems"));
        assertEquals(1, counts.get("importantFacts"));
        assertEquals(1, counts.get("commitments"));
        assertEquals(1, ((List<?>) pack.get("commitments")).size());
        assertEquals("Can", ((Map<?, ?>) ((List<?>) pack.get("commitments")).getFirst()).get("owner"));
        assertTrue(pack.get("qualityFlags").toString().contains("CONSISTENCY_AUDIT_PASSED"));
        assertEquals(noteId.toString(), ((Map<?, ?>) pack.get("ids")).get("noteId"));
    }
}
