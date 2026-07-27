package com.nanobaseai.actenora.aiprocessing.application.pipeline.staged;

import com.nanobaseai.actenora.aiprocessing.application.pipeline.InferenceRequest;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.InferenceResponse;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ModelDescriptor;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ModelRuntimePort;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.PriorMeetingContextPort;
import com.nanobaseai.actenora.aiprocessing.application.port.ApprovedKnowledgeIndexPort;
import com.nanobaseai.actenora.aiprocessing.application.port.MeetingNoteHandoffPort;
import com.nanobaseai.actenora.aiprocessing.application.port.TranscriptSegmentSourcePort;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiCapability;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.JobPriority;
import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingArtifact;
import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingStage;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryProcessingArtifactRepository;
import com.nanobaseai.actenora.aiprocessing.infrastructure.prompt.InMemoryPromptRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinutesExecutorEvidenceAllowlistTest {

    @Test
    void minutesPassesTranscriptSegmentIdsAndSurfacesSynthesisFallback() {
        UUID tenant = UUID.randomUUID();
        UUID meeting = UUID.randomUUID();
        UUID transcript = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-27T20:00:00Z");

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
                "minutes-" + meeting,
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
                          "topics": [{"text":"Delivery","evidenceSegmentIds":["seg-1"],"confidence":0.9}],
                          "decisions": [{"text":"Ship Friday","evidenceSegmentIds":["seg-1"],"confidence":0.9}],
                          "actionItems": [],
                          "risks": [],
                          "openQuestions": [],
                          "commitments": [],
                          "qualityFlags": [],
                          "evidenceSegmentIds": ["seg-1"],
                          "confidence": 0.9
                        }
                        """,
                now
        ));

        AtomicReference<InferenceRequest> captured = new AtomicReference<>();
        ModelRuntimePort runtime = new ModelRuntimePort() {
            @Override
            public ModelDescriptor descriptor() {
                return new ModelDescriptor("catalog", "served-local", "v1", 8192, 2048);
            }

            @Override
            public InferenceResponse infer(InferenceRequest request) {
                captured.set(request);
                throw new IllegalStateException("forced synthesis failure");
            }

            @Override
            public boolean healthy() {
                return true;
            }
        };

        TranscriptSegmentSourcePort segments = (t, transcriptId) -> List.of(
                new SegmentInput("seg-1", 0, "Ada", 0, 1_000, "We decided to ship Friday.", false),
                new SegmentInput("seg-2", 1, "Bob", 1_000, 2_000, "I will own the release notes.", false)
        );

        AtomicReference<FinalNoteDraft> handedOff = new AtomicReference<>();
        MeetingNoteHandoffPort handoff = command -> {
            handedOff.set(command.draft());
            return Optional.of(UUID.randomUUID());
        };

        StageExecutor executor = DefaultStageExecutors.createAll(
                new InMemoryPromptRegistry(),
                runtime,
                segments,
                artifacts,
                PriorMeetingContextPort.noop(),
                handoff,
                ApprovedKnowledgeIndexPort.noop()
        ).get(ProcessingStage.MINUTES);

        StageExecutionResult result = executor.execute(job, now);

        assertTrue(result.succeeded(), () -> "minutes should soft-succeed: " + result.errorMessage());
        assertNotNull(captured.get());
        assertEquals(Set.of("seg-1", "seg-2"), new HashSet<>(captured.get().allowedEvidenceSegmentIds()));
        assertTrue(handedOff.get().qualityFlags().contains("SYNTHESIS_FALLBACK"));
        assertTrue(result.artifactJson().contains("SYNTHESIS_FALLBACK"));
    }
}
