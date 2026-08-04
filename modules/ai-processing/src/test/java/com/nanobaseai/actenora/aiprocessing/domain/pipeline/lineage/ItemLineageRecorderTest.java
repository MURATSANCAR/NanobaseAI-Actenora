package com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.CommitmentCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.consistency.CrossTypeMeetingItemSubsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemLineageRecorderTest {

    @AfterEach
    void tearDown() {
        ItemLineageRecorder.clear();
    }

    @Test
    void serializationExposesStableSafeMap() {
        ItemLineageRecord record = new ItemLineageRecord(
                "chunk-2-action-03",
                "ACTION_ITEM",
                LineageStage.CROSS_TYPE_RESOLUTION,
                LineageOperation.KEEP,
                LineageReasonCode.POLICY_KEEP,
                List.of("chunk-2-decision-01"),
                ItemLineageRecord.snapshot("text-before", "Selin", "bugün 16.00", List.of("seg-25")),
                ItemLineageRecord.snapshot("text-after", "Selin", "bugün 16.00", List.of("seg-25")),
                "cross-type-existing-v1",
                Instant.parse("2026-08-03T12:00:00Z"),
                "meeting-1",
                "job-1",
                "chunk-2"
        );
        Map<String, Object> map = record.toSafeMap();
        assertEquals("chunk-2-action-03", map.get("candidateId"));
        assertEquals("ACTION_ITEM", map.get("candidateType"));
        assertEquals("CROSS_TYPE_RESOLUTION", map.get("stage"));
        assertEquals("KEEP", map.get("operation"));
        assertEquals("POLICY_KEEP", map.get("reasonCode"));
        assertEquals(List.of("chunk-2-decision-01"), map.get("relatedCandidateIds"));
        assertEquals("2026-08-03T12:00:00Z", map.get("timestamp"));
    }

    @Test
    void dropRequiresReasonCode() {
        assertThrows(NullPointerException.class, () -> new ItemLineageRecord(
                "c1",
                "ACTION_ITEM",
                LineageStage.CROSS_TYPE_RESOLUTION,
                LineageOperation.DROP,
                null,
                List.of(),
                Map.of(),
                Map.of(),
                "v1",
                Instant.now(),
                null,
                null,
                null
        ));
    }

    @Test
    void rejectRequiresReasonCode() {
        assertThrows(NullPointerException.class, () -> new ItemLineageRecord(
                "c1",
                "ACTION_ITEM",
                LineageStage.SCHEMA_VALIDATION,
                LineageOperation.REJECT,
                null,
                List.of(),
                null,
                Map.of(),
                Map.of(),
                "v1",
                Instant.now(),
                null,
                null,
                null
        ));
    }

    @Test
    void lineagePersistenceFailureDoesNotFailPipeline() {
        ItemLineageRecorder.install(ItemLineageRecorder.enabled());
        LineageSupport.recordSafely(null); // must not throw
        assertTrue(true);
    }

    @Test
    void parentCandidateIdSerialized() {
        ItemLineageRecord record = new ItemLineageRecord(
                "child",
                "ACTION_ITEM",
                LineageStage.ACTION_POST_PROCESSING,
                LineageOperation.CREATE,
                LineageReasonCode.ACTION_COMPOUND_SPLIT,
                List.of("parent"),
                "parent",
                Map.of(),
                ItemLineageRecord.snapshot("x", "Selin", null, List.of("27")),
                "v1",
                Instant.now(),
                null,
                null,
                null
        );
        assertEquals("parent", record.parentCandidateId());
        assertEquals("parent", record.toSafeMap().get("parentCandidateId"));
    }

    @Test
    void mergeRecordsSourceCandidateIds() {
        ItemLineageRecorder recorder = ItemLineageRecorder.enabled();
        ItemLineageRecorder.install(recorder);
        recorder.record(new ItemLineageRecord(
                "merged-action-01",
                "ACTION_ITEM",
                LineageStage.MERGE,
                LineageOperation.MERGE,
                LineageReasonCode.MERGED_AS_DUPLICATE,
                List.of("chunk-1-action-01", "chunk-2-action-04"),
                ItemLineageRecord.snapshot("a", null, null, List.of("1")),
                ItemLineageRecord.snapshot("a", null, null, List.of("1", "2")),
                "merge-v1",
                Instant.now(),
                "m",
                "j",
                null
        ));
        assertEquals(1, recorder.size());
        assertEquals(
                List.of("chunk-1-action-01", "chunk-2-action-04"),
                recorder.snapshot().getFirst().relatedCandidateIds()
        );
    }

    @Test
    void splitRecordsParentCandidateId() {
        ItemLineageRecorder recorder = ItemLineageRecorder.enabled();
        ItemLineageRecorder.install(recorder);
        recorder.record(new ItemLineageRecord(
                "action-child-a",
                "ACTION_ITEM",
                LineageStage.ACTION_POST_PROCESSING,
                LineageOperation.CREATE,
                LineageReasonCode.ACTION_COMPOUND_SPLIT,
                List.of("parent-compound-27"),
                Map.of(),
                ItemLineageRecord.snapshot("child a", "Selin", null, List.of("27")),
                "action-split-v1",
                Instant.now(),
                null,
                null,
                null
        ));
        assertEquals("parent-compound-27", recorder.snapshot().getFirst().relatedCandidateIds().getFirst());
        assertEquals(LineageReasonCode.ACTION_COMPOUND_SPLIT, recorder.snapshot().getFirst().reasonCode());
    }

    @Test
    void lineageDisabledDoesNotChangeCrossTypeOutcome() {
        DecisionCandidate decision = new DecisionCandidate(
                "Paralel refresh çağrıları tek promise üzerinde birleştirilecek.",
                List.of("seg-25"),
                0.9
        );
        ActionItemCandidate action = new ActionItemCandidate(
                "Paralel refresh çağrılarını tek promise üzerinde birleştirecek.",
                "Selin",
                null,
                List.of("seg-25"),
                0.9
        );
        CommitmentCandidate commitment = new CommitmentCandidate(
                "Paralel refresh çağrılarını tek promise üzerinde birleştireceğim.",
                "Selin",
                List.of("seg-25"),
                0.8
        );

        CrossTypeMeetingItemSubsumer subsumer = new CrossTypeMeetingItemSubsumer();

        ItemLineageRecorder.install(ItemLineageRecorder.disabled());
        CrossTypeMeetingItemSubsumer.Outcome disabled = subsumer.apply(
                List.of(decision), List.of(action), List.of(commitment)
        );

        ItemLineageRecorder.install(ItemLineageRecorder.enabled());
        CrossTypeMeetingItemSubsumer.Outcome enabled = subsumer.apply(
                List.of(decision), List.of(action), List.of(commitment)
        );

        assertEquals(disabled.actions().size(), enabled.actions().size());
        assertEquals(disabled.commitments().size(), enabled.commitments().size());
        assertEquals(disabled.actionsDropped(), enabled.actionsDropped());
        assertEquals(disabled.commitmentsDropped(), enabled.commitmentsDropped());
        assertFalse(ItemLineageRecorder.current().snapshot().isEmpty());
    }

    @Test
    void recorderFailuresNeverPropagate() {
        ItemLineageRecorder recorder = ItemLineageRecorder.enabled();
        ItemLineageRecorder.install(recorder);
        recorder.record(null);
        assertEquals(0, recorder.size());
        assertTrue(recorder.isEnabled());
    }
}
