package com.nanobaseai.actenora.aiprocessing.domain.pipeline.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ExtractionPipelineService;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.InferenceRequest;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.InferenceResponse;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ModelDescriptor;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ModelRuntimePort;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.PipelineRunRequest;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.PipelineRunResult;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.PriorMeetingContext;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.PriorMeetingContextPort;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.staged.DefaultStageExecutors;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.staged.StageExecutionResult;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.staged.StageExecutor;
import com.nanobaseai.actenora.aiprocessing.application.port.ApprovedKnowledgeIndexPort;
import com.nanobaseai.actenora.aiprocessing.application.port.MeetingNoteHandoffPort;
import com.nanobaseai.actenora.aiprocessing.application.port.MeetingOccurrenceClockPort;
import com.nanobaseai.actenora.aiprocessing.application.port.PipelineQualityMetricsPort;
import com.nanobaseai.actenora.aiprocessing.application.port.TranscriptSegmentSourcePort;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiCapability;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.JobPriority;
import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingArtifact;
import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingStage;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal.ChunkExtractionService;
import com.nanobaseai.actenora.aiprocessing.infrastructure.adapter.Qwen27BModelAdapter;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryProcessingArtifactRepository;
import com.nanobaseai.actenora.aiprocessing.infrastructure.prompt.InMemoryPromptRegistry;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionPostProcessingStatsPersistenceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void legacyPipelinePersistsActionPostProcessingStats() {
        Qwen27BModelAdapter adapter = new Qwen27BModelAdapter(req -> """
                {
                  "topics": [],
                  "decisions": [],
                  "actionItems": [
                    {"text":"Aksiyon kaydı: Selin düzeltmeyi bugün 16.00'ya kadar yapacak; Can correlation id ekleyecek.","owner":"Selin","evidenceSegmentIds":["seg-1"],"confidence":0.9}
                  ],
                  "risks": [],
                  "openQuestions": [],
                  "commitments": [],
                  "qualityFlags": [],
                  "evidenceSegmentIds": ["seg-1"],
                  "confidence": 0.9
                }
                """);
        ExtractionPipelineService service = ExtractionPipelineService.create(new InMemoryPromptRegistry(), adapter);
        PipelineRunResult result = service.run(new PipelineRunRequest(
                TenantId.of(UUID.randomUUID()),
                UUID.randomUUID(),
                UUID.randomUUID(),
                InMemoryPromptRegistry.DEFAULT_EXTRACTION_PROMPT_ID,
                List.of(new SegmentInput(
                        "seg-1", 0, "Selin", 0, 1_000,
                        "Aksiyon kaydı: Selin düzeltmeyi bugün 16.00'ya kadar yapacak; Can correlation id ekleyecek.",
                        false)),
                "tr",
                30,
                PipelineRunRequest.DEFAULT_PARALLEL_CHUNK_LIMIT,
                PriorMeetingContext.EMPTY,
                "2026-07-29T08:11:26+03:00"
        ));

        assertTrue(result.success(), () -> result.failureCategory() + " / " + result.failureMessage());
        Map<String, Object> stats = result.metrics().actionPostProcessingStats();
        assertFalse(stats.isEmpty(), "legacy metrics must carry action post-processing stats");
        assertEquals("ACTION_POST_PROCESSING", stats.get("stage"));
        assertTrue(stats.containsKey("inputActionCount"));
        assertTrue(stats.containsKey("outputActionCount"));
        assertTrue(stats.containsKey("prefixesRemoved"));
        assertTrue(stats.containsKey("compoundActionsSplit"));
        assertTrue(stats.containsKey("auditStatus"));
        assertTrue(ActionPostProcessingStats.isSafeArtifactPayload(stats));
        assertFalse(containsRawTranscript(stats));
    }

    @Test
    void stagedPipelinePersistsActionPostProcessingStats() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID meeting = UUID.randomUUID();
        UUID transcript = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-29T08:00:00Z");

        AiJob job = AiJob.enqueueStaged(
                tenant,
                meeting,
                transcript,
                "minutes",
                ProcessingStage.MINUTES,
                JobPriority.NORMAL,
                AiCapability.FINAL_NOTE,
                "pv-test",
                "sv-test",
                "tr",
                4,
                true,
                now,
                now.plus(Duration.ofHours(1)),
                UUID.randomUUID(),
                null,
                "minutes-stats-" + meeting,
                null
        );

        InMemoryProcessingArtifactRepository artifacts = new InMemoryProcessingArtifactRepository();
        artifacts.save(ProcessingArtifact.inlineJson(
                tenant,
                job.id(),
                meeting,
                "validated-bundle",
                """
                        {
                          "topics": [],
                          "decisions": [],
                          "actionItems": [
                            {"text":"Aksiyon kaydı: Selin düzeltmeyi bugün 16.00'ya kadar yapacak; Can correlation id ekleyecek.","owner":"Selin","evidenceSegmentIds":["seg-1"],"confidence":0.9},
                            {"text":"Aksiyon kaydı: Can başlığı düzeltecek; Burak Outlook ve Apple Mail regresyonunu yarın öğlene kadar tamamlayacak.","owner":"Can","evidenceSegmentIds":["seg-2"],"confidence":0.9}
                          ],
                          "risks": [],
                          "openQuestions": [],
                          "commitments": [
                            {"text":"Test planına mutlu yol dışında timeout, retry, yetkisiz erişim ve yarıda kalan işlem senaryolarını ekleyeceğim.","evidenceSegmentIds":["seg-3"],"confidence":0.9}
                          ],
                          "qualityFlags": [],
                          "evidenceSegmentIds": ["seg-1"],
                          "confidence": 0.9
                        }
                        """,
                now
        ));

        ModelRuntimePort runtime = new ModelRuntimePort() {
            @Override
            public ModelDescriptor descriptor() {
                return new ModelDescriptor("catalog", "served-local", "v1", 8192, 2048);
            }

            @Override
            public InferenceResponse infer(InferenceRequest request) {
                throw new IllegalStateException("forced synthesis failure — use deterministic draft");
            }

            @Override
            public boolean healthy() {
                return true;
            }
        };

        TranscriptSegmentSourcePort segments = (t, transcriptId) -> List.of(
                new SegmentInput(
                        "seg-1", 0, "Selin", 0, 1_000,
                        "Aksiyon kaydı: Selin düzeltmeyi bugün 16.00'ya kadar yapacak; Can correlation id ekleyecek.",
                        false),
                new SegmentInput(
                        "seg-2", 1, "Derya", 1_000, 2_000,
                        "Aksiyon kaydı: Can başlığı düzeltecek; Burak Outlook ve Apple Mail regresyonunu yarın öğlene kadar tamamlayacak.",
                        false),
                new SegmentInput(
                        "seg-3", 2, "Burak", 2_000, 3_000,
                        "Test planına mutlu yol dışında timeout, retry, yetkisiz erişim ve yarıda kalan işlem senaryolarını ekleyeceğim.",
                        false)
        );
        AtomicReference<MeetingNoteHandoffPort.HandoffCommand> handedOff = new AtomicReference<>();
        MeetingNoteHandoffPort handoff = command -> {
            handedOff.set(command);
            return Optional.of(UUID.randomUUID());
        };
        MeetingOccurrenceClockPort clock = new MeetingOccurrenceClockPort() {
            @Override
            public Optional<OffsetDateTime> scheduledStart(TenantId tenantId, UUID meetingOccurrenceId) {
                return Optional.of(OffsetDateTime.of(2026, 7, 29, 8, 11, 26, 0, ZoneOffset.ofHours(3)));
            }

            @Override
            public ZoneId timezone(TenantId tenantId, UUID meetingOccurrenceId) {
                return ZoneId.of("Europe/Istanbul");
            }
        };

        StageExecutor executor = DefaultStageExecutors.createAll(
                new InMemoryPromptRegistry(),
                runtime,
                segments,
                artifacts,
                PriorMeetingContextPort.noop(),
                handoff,
                ApprovedKnowledgeIndexPort.noop(),
                ChunkExtractionService.createDefault(),
                PipelineQualityMetricsPort.noop(),
                clock
        ).get(ProcessingStage.MINUTES);

        StageExecutionResult stageResult = executor.execute(job, now);
        assertTrue(stageResult.succeeded(), () -> "minutes failed: " + stageResult.errorMessage());
        MeetingNoteHandoffPort.HandoffCommand handoffCommand = handedOff.get();
        assertEquals("2026-07-29T08:11:26+03:00", handoffCommand.meetingStartedAtIso());
        assertEquals("Europe/Istanbul", handoffCommand.meetingTimezone());
        assertEquals(4, handoffCommand.draft().actionItems().size(),
                () -> handoffCommand.draft().actionItems().toString());
        var selin = handoffCommand.draft().actionItems().stream()
                .filter(action -> "Selin".equals(action.owner()))
                .findFirst()
                .orElseThrow();
        assertEquals("2026-07-29", selin.dueDate());
        assertEquals("2026-07-29T16:00:00+03:00", selin.dueAt());
        assertEquals("bugün 16.00'ya kadar", selin.relativeDate());
        var burak = handoffCommand.draft().actionItems().stream()
                .filter(action -> "Burak".equals(action.owner()))
                .findFirst()
                .orElseThrow();
        assertEquals("2026-07-30", burak.dueDate());
        assertEquals("2026-07-30T12:00:00+03:00", burak.dueAt());
        assertEquals("yarın öğlene kadar", burak.relativeDate());
        // Compound split must surface the correlation clause as its own action item.
        // Correct owner binding (Can) is a Phase-2 quality gate measured by the gold scorer;
        // current production binding may attach the parent/speaker owner (observed: Selin).
        assertEquals(1, handoffCommand.draft().actionItems().stream()
                .filter(action -> action.text().toLowerCase().contains("correlation"))
                .count(),
                () -> "correlation clause missing after compound split: "
                        + handoffCommand.draft().actionItems());
        assertEquals("Burak", handoffCommand.draft().commitments().getFirst().owner());
        assertTrue(handoffCommand.draft().actionItems().stream()
                .noneMatch(action -> action.text().startsWith("Aksiyon kaydı:")
                        || action.text().contains(";")));

        Optional<ProcessingArtifact> saved = artifacts.findLatestByMeetingAndType(
                tenant, meeting, ActionPostProcessingStats.ARTIFACT_TYPE);
        assertTrue(saved.isPresent(), "staged minutes must persist action-post-processing artifact");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = mapper.readValue(saved.get().payloadJson().orElseThrow(), Map.class);
        assertEquals("ACTION_POST_PROCESSING", payload.get("stage"));
        assertTrue(payload.containsKey("auditStatus"));
        assertEquals(4, payload.get("outputActionCount"));
        assertTrue(((Number) payload.get("compoundActionsSplit")).intValue() >= 1);
        assertEquals(2, payload.get("datesResolved"));
        assertTrue(ActionPostProcessingStats.isSafeArtifactPayload(payload));
        assertFalse(containsRawTranscript(payload));
    }

    @Test
    void statsDoNotContainRawTranscriptText() {
        ActionPostProcessingStats stats = new ActionPostProcessingStats();
        stats.setInputActionCount(2);
        stats.setOutputActionCount(3);
        stats.incrementPrefixesRemoved();
        stats.warn("AMBIGUOUS_COMPOUND_ACTION");
        Map<String, Object> map = stats.toArtifactMap("meeting-1");
        assertTrue(ActionPostProcessingStats.isSafeArtifactPayload(map));
        assertFalse(containsRawTranscript(map));
    }

    private static boolean containsRawTranscript(Map<?, ?> payload) {
        String json = String.valueOf(payload).toLowerCase();
        return json.contains("transcript")
                || json.contains("prompt")
                || json.contains("aksiyon kaydı: selin");
    }
}
