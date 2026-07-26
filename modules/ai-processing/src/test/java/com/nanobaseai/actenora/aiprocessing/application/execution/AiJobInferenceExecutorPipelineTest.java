package com.nanobaseai.actenora.aiprocessing.application.execution;

import com.nanobaseai.actenora.aiprocessing.application.AiJobService;
import com.nanobaseai.actenora.aiprocessing.application.admission.DefaultAdmissionController;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderFailureCategory;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ExtractionPipelineService;
import com.nanobaseai.actenora.aiprocessing.application.port.AdmissionController;
import com.nanobaseai.actenora.aiprocessing.application.port.LocalModelProviderLocator;
import com.nanobaseai.actenora.aiprocessing.application.port.RoutableCandidate;
import com.nanobaseai.actenora.aiprocessing.application.routing.CapabilityModelRouter;
import com.nanobaseai.actenora.aiprocessing.application.scheduling.FairJobScheduler;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiCapability;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJobStatus;
import com.nanobaseai.actenora.aiprocessing.domain.job.JobPriority;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FailureCategory;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.infrastructure.adapter.LocalProviderModelRuntimeAdapter;
import com.nanobaseai.actenora.aiprocessing.infrastructure.adapter.Qwen27BModelAdapter;
import com.nanobaseai.actenora.aiprocessing.infrastructure.llm.MockLocalProvider;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryAiAttemptRepository;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryAiJobRepository;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryModelCatalog;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryTenantAiPolicy;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryTranscriptSegmentSource;
import com.nanobaseai.actenora.aiprocessing.infrastructure.prompt.InMemoryPromptRegistry;
import com.nanobaseai.actenora.aiprocessing.infrastructure.prompt.PromptRegistryInferenceInputResolver;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiJobInferenceExecutorPipelineTest {

    private static final Instant NOW = Instant.parse("2026-07-25T14:00:00Z");

    private final UUID tenantId = UUID.randomUUID();
    private InMemoryAiJobRepository jobs;
    private InMemoryAiAttemptRepository attempts;
    private InMemoryTranscriptSegmentSource segments;
    private MockLocalProvider provider;
    private AiJobService service;
    private AiJobInferenceExecutor executor;
    private UUID transcriptId;

    @BeforeEach
    void setUp() {
        jobs = new InMemoryAiJobRepository();
        attempts = new InMemoryAiAttemptRepository();
        segments = new InMemoryTranscriptSegmentSource();
        transcriptId = UUID.randomUUID();

        InMemoryModelCatalog catalog = new InMemoryModelCatalog();
        InMemoryTenantAiPolicy policy = new InMemoryTenantAiPolicy();
        policy.allow(tenantId, "local-extract");
        policy.setMaxConcurrentAiJobs(tenantId, 4);

        UUID modelId = UUID.randomUUID();
        catalog.add(new RoutableCandidate(
                modelId,
                "local-extract",
                UUID.randomUUID(),
                "extract-a",
                Set.of(AiCapability.TRANSCRIPT_EXTRACTION),
                8192,
                0,
                Set.of("tr", "en"),
                true,
                true,
                true,
                4,
                0,
                0,
                0.9,
                0.8,
                10
        ));

        var router = new CapabilityModelRouter(catalog, policy);
        var scheduler = new FairJobScheduler(jobs, attempts, policy, router);
        var admission = new DefaultAdmissionController(jobs, policy, router, scheduler);
        service = new AiJobService(admission, jobs, attempts, scheduler);

        provider = new MockLocalProvider(2, true, Set.of(Qwen27BModelAdapter.SERVED_MODEL_ID));
        var runtime = LocalProviderModelRuntimeAdapter.qwen27B(provider, modelId);
        var pipeline = ExtractionPipelineService.create(new InMemoryPromptRegistry(), runtime);

        executor = new AiJobInferenceExecutor(
                service,
                LocalModelProviderLocator.single(provider),
                new PromptRegistryInferenceInputResolver(new InMemoryPromptRegistry()),
                id -> Optional.of(Qwen27BModelAdapter.SERVED_MODEL_ID),
                pipeline,
                segments,
                AiJobInferenceExecutor.DEFAULT_MAX_ATTEMPTS,
                AiJobInferenceExecutor.DEFAULT_MAX_TIMEOUT_SECONDS
        );
    }

    @Test
    void extractionJobRunsPipelineAndSucceeds() {
        seedSegments();
        provider.setResponse(validExtractionJson());
        UUID jobId = submitExtraction().job().id();

        var outcome = executor.executeNext(NOW).orElseThrow();

        assertTrue(outcome.succeeded());
        assertEquals(jobId, outcome.jobId());
        assertEquals(AiJobStatus.SUCCEEDED, jobs.findById(jobId).orElseThrow().status());
        assertTrue(attempts.findByJobId(jobId).getFirst().outputTokens().orElseThrow() > 0);
    }

    @Test
    void emptySegmentsFailPermanently() {
        provider.setResponse(validExtractionJson());
        UUID jobId = submitExtraction().job().id();

        var outcome = executor.executeNext(NOW).orElseThrow();

        assertFalse(outcome.succeeded());
        assertFalse(outcome.retryable());
        assertEquals(ProviderFailureCategory.UNKNOWN, outcome.failure().orElseThrow());
        assertEquals(AiJobStatus.DEAD, jobs.findById(jobId).orElseThrow().status());
        assertEquals(
                FailureCategory.EVIDENCE_MISSING.name(),
                attempts.findByJobId(jobId).getFirst().failureCategory().orElseThrow());
    }

    @Test
    void modelUnavailableIsRetryable() {
        seedSegments();
        provider.setHealth(com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderHealth.down(
                "down", 0));
        UUID jobId = submitExtraction().job().id();

        var outcome = executor.executeNext(NOW).orElseThrow();

        assertFalse(outcome.succeeded());
        assertTrue(outcome.retryable());
        assertEquals(AiJobStatus.QUEUED, jobs.findById(jobId).orElseThrow().status());
    }

    @Test
    void unrepairableJsonFailsPermanently() {
        seedSegments();
        provider.setResponse("{not-json");
        UUID jobId = submitExtraction().job().id();

        var outcome = executor.executeNext(NOW).orElseThrow();

        assertFalse(outcome.succeeded());
        assertFalse(outcome.retryable());
        assertEquals(AiJobStatus.DEAD, jobs.findById(jobId).orElseThrow().status());
    }

    private void seedSegments() {
        segments.put(TenantId.of(tenantId), transcriptId, List.of(
                new SegmentInput("seg-1", 0, "Alice", 0, 1_000, "We decided to ship Friday.", true)
        ));
    }

    private AdmissionController.AdmissionDecision submitExtraction() {
        return service.submit(new AdmissionController.SubmitAiJobCommand(
                tenantId,
                UUID.randomUUID(),
                transcriptId,
                "CHUNK_EXTRACTION",
                JobPriority.NORMAL,
                AiCapability.TRANSCRIPT_EXTRACTION,
                "prompt-v1",
                "schema-v1",
                "tr",
                1000,
                null,
                UUID.randomUUID(),
                NOW
        ));
    }

    private static String validExtractionJson() {
        return """
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
                """;
    }
}
