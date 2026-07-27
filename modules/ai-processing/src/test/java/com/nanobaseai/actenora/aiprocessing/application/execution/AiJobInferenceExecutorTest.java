package com.nanobaseai.actenora.aiprocessing.application.execution;

import com.nanobaseai.actenora.aiprocessing.application.AiJobService;
import com.nanobaseai.actenora.aiprocessing.application.admission.DefaultAdmissionController;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderFailureCategory;
import com.nanobaseai.actenora.aiprocessing.application.port.AdmissionController;
import com.nanobaseai.actenora.aiprocessing.application.port.LocalModelProviderLocator;
import com.nanobaseai.actenora.aiprocessing.application.port.RoutableCandidate;
import com.nanobaseai.actenora.aiprocessing.application.port.ServedModelResolverPort;
import com.nanobaseai.actenora.aiprocessing.application.routing.CapabilityModelRouter;
import com.nanobaseai.actenora.aiprocessing.application.scheduling.FairJobScheduler;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiAttemptStatus;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiCapability;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJobStatus;
import com.nanobaseai.actenora.aiprocessing.domain.job.JobPriority;
import com.nanobaseai.actenora.aiprocessing.infrastructure.llm.MockLocalProvider;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryAiAttemptRepository;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryAiJobRepository;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryModelCatalog;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryTenantAiPolicy;
import com.nanobaseai.actenora.aiprocessing.infrastructure.prompt.InMemoryPromptRegistry;
import com.nanobaseai.actenora.aiprocessing.infrastructure.prompt.PromptRegistryInferenceInputResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiJobInferenceExecutorTest {

    private static final String SERVED_MODEL_ID = "qwen-local";

    private final UUID tenantId = UUID.randomUUID();
    private Instant now;

    private InMemoryAiJobRepository jobs;
    private InMemoryAiAttemptRepository attempts;
    private MockLocalProvider provider;
    private AiJobService service;
    private AiJobInferenceExecutor executor;
    private UUID modelId;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-07-25T12:00:00Z");
        jobs = new InMemoryAiJobRepository();
        attempts = new InMemoryAiAttemptRepository();
        InMemoryModelCatalog catalog = new InMemoryModelCatalog();
        InMemoryTenantAiPolicy policy = new InMemoryTenantAiPolicy();
        policy.allow(tenantId, "local-final");
        policy.setMaxConcurrentAiJobs(tenantId, 4);

        modelId = UUID.randomUUID();
        catalog.add(new RoutableCandidate(
                modelId,
                "local-final",
                UUID.randomUUID(),
                "final-a",
                Set.of(AiCapability.FINAL_NOTE),
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
                0.6,
                10
        ));

        var router = new CapabilityModelRouter(catalog, policy);
        var scheduler = new FairJobScheduler(jobs, attempts, policy, router);
        var admission = new DefaultAdmissionController(jobs, policy, router, scheduler);
        service = new AiJobService(admission, jobs, attempts, scheduler);

        provider = new MockLocalProvider(2, true, Set.of(SERVED_MODEL_ID));
        executor = new AiJobInferenceExecutor(
                service,
                LocalModelProviderLocator.single(provider),
                new PromptRegistryInferenceInputResolver(new InMemoryPromptRegistry()),
                modelDefinitionId -> Optional.of(SERVED_MODEL_ID)
        );
    }

    @Test
    void claimedJobRunsInferenceAndSucceeds() {
        provider.setResponse("{\"note\":\"ok\"}");
        UUID jobId = submit().job().id();

        var outcome = executor.executeNext(now).orElseThrow();

        assertTrue(outcome.succeeded());
        assertEquals(jobId, outcome.jobId());
        assertEquals(AiJobStatus.SUCCEEDED, outcome.jobStatus());
        assertEquals(AiJobStatus.SUCCEEDED, jobs.findById(jobId).orElseThrow().status());

        var attempt = attempts.findByJobId(jobId).getFirst();
        assertEquals(AiAttemptStatus.SUCCEEDED, attempt.status());
        assertTrue(attempt.outputTokens().orElseThrow() > 0);
    }

    @Test
    void retryableProviderFailureRequeuesJob() {
        provider.forceFailure(ProviderFailureCategory.READ_TIMEOUT);
        UUID jobId = submit().job().id();

        var outcome = executor.executeNext(now).orElseThrow();

        assertFalse(outcome.succeeded());
        assertTrue(outcome.retryable());
        assertEquals(ProviderFailureCategory.READ_TIMEOUT, outcome.failure().orElseThrow());
        assertEquals(AiJobStatus.QUEUED, jobs.findById(jobId).orElseThrow().status());
        assertEquals(AiAttemptStatus.FAILED, attempts.findByJobId(jobId).getFirst().status());
    }

    @Test
    void permanentProviderFailureKillsJob() {
        provider.forceFailure(ProviderFailureCategory.MODEL_MISMATCH);
        UUID jobId = submit().job().id();

        var outcome = executor.executeNext(now).orElseThrow();

        assertFalse(outcome.succeeded());
        assertFalse(outcome.retryable());
        assertEquals(AiJobStatus.DEAD, jobs.findById(jobId).orElseThrow().status());
    }

    @Test
    void retryableFailureStopsAtMaxAttempts() {
        provider.forceFailure(ProviderFailureCategory.READ_TIMEOUT);
        UUID jobId = submit().job().id();

        Instant cursor = now;
        for (int i = 0; i < AiJobInferenceExecutor.DEFAULT_MAX_ATTEMPTS; i++) {
            executor.executeNext(cursor).orElseThrow();
            cursor = cursor.plus(AiJob.RETRY_BACKOFF_CAP).plusSeconds(1);
        }

        assertEquals(AiJobStatus.DEAD, jobs.findById(jobId).orElseThrow().status());
        assertEquals(AiJobInferenceExecutor.DEFAULT_MAX_ATTEMPTS, attempts.findByJobId(jobId).size());
    }

    @Test
    void unknownServedModelFailsAttemptWithoutRetry() {
        executor = new AiJobInferenceExecutor(
                service,
                LocalModelProviderLocator.single(provider),
                new PromptRegistryInferenceInputResolver(new InMemoryPromptRegistry()),
                ServedModelResolverPort.none()
        );
        UUID jobId = submit().job().id();

        var outcome = executor.executeNext(now).orElseThrow();

        assertFalse(outcome.succeeded());
        assertEquals(ProviderFailureCategory.INVALID_SERVED_MODEL, outcome.failure().orElseThrow());
        assertEquals(AiJobStatus.DEAD, jobs.findById(jobId).orElseThrow().status());
    }

    @Test
    void emptyQueueYieldsNoOutcome() {
        assertTrue(executor.executeNext(now).isEmpty());
    }

    private AdmissionController.AdmissionDecision submit() {
        return service.submit(new AdmissionController.SubmitAiJobCommand(
                tenantId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "FINAL_NOTE",
                JobPriority.NORMAL,
                AiCapability.FINAL_NOTE,
                "prompt-v1",
                "schema-v1",
                "tr",
                1000,
                null,
                UUID.randomUUID(),
                now
        ));
    }
}
