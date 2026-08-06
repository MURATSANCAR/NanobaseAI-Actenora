package com.nanobaseai.actenora.aiprocessing.application.admission;

import com.nanobaseai.actenora.aiprocessing.application.port.AdmissionController;
import com.nanobaseai.actenora.aiprocessing.application.port.ModelRouter;
import com.nanobaseai.actenora.aiprocessing.application.port.TenantAiPolicyPort;
import com.nanobaseai.actenora.aiprocessing.application.scheduling.FairJobScheduler;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiCapability;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJobStatus;
import com.nanobaseai.actenora.aiprocessing.domain.job.JobPriority;
import com.nanobaseai.actenora.aiprocessing.domain.job.SelectedRoute;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryAiAttemptRepository;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryAiJobRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscriptIdempotencyAdmissionTest {

    @Test
    void rejectsNewTranscriptWhileSameMeetingExtractionIsActive() {
        InMemoryAiJobRepository jobs = new InMemoryAiJobRepository();
        FairJobScheduler scheduler = new FairJobScheduler(
                jobs, new InMemoryAiAttemptRepository(), permissivePolicy(), successRouter());
        DefaultAdmissionController admission = new DefaultAdmissionController(
                jobs, permissivePolicy(), successRouter(), scheduler);
        UUID tenant = UUID.randomUUID();
        UUID meeting = UUID.randomUUID();
        UUID firstTranscript = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-06T10:00:00Z");

        assertTrue(admission.admit(command(tenant, meeting, firstTranscript, now)).admitted());
        AdmissionController.AdmissionDecision second = admission.admit(
                command(tenant, meeting, UUID.randomUUID(), now.plusSeconds(1)));

        assertFalse(second.admitted());
        assertTrue(second.rejectReason().contains("meeting_already_processing"));
    }

    @Test
    void rejectsSucceededExtractionUnlessForceReprocess() {
        InMemoryAiJobRepository jobs = new InMemoryAiJobRepository();
        FairJobScheduler scheduler = new FairJobScheduler(
                jobs, new InMemoryAiAttemptRepository(), permissivePolicy(), successRouter());
        DefaultAdmissionController admission = new DefaultAdmissionController(
                jobs, permissivePolicy(), successRouter(), scheduler);

        UUID tenant = UUID.randomUUID();
        UUID meeting = UUID.randomUUID();
        UUID transcript = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-27T18:00:00Z");

        AiJob prior = AiJob.enqueue(
                tenant, meeting, transcript, "CHUNK_EXTRACTION", JobPriority.NORMAL,
                AiCapability.TRANSCRIPT_EXTRACTION, "pv", "sv", "tr", 10, true,
                now, now.plus(Duration.ofHours(1)), transcript
        );
        prior.applyRoute(new SelectedRoute(
                UUID.fromString("00000000-0000-4000-8000-000000000001"),
                UUID.fromString("00000000-0000-4000-8000-000000000002"),
                "local", "test", List.of(), now
        ));
        prior.markRunning(now);
        prior.markSucceeded(1, 1, now.plusSeconds(1));
        jobs.save(prior);
        assertTrue(prior.status() == AiJobStatus.SUCCEEDED);

        AdmissionController.AdmissionDecision blocked = admission.admit(new AdmissionController.SubmitAiJobCommand(
                tenant, meeting, transcript, "CHUNK_EXTRACTION", JobPriority.NORMAL,
                AiCapability.TRANSCRIPT_EXTRACTION, "pv", "sv", "tr", 10, null,
                UUID.randomUUID(), now.plusSeconds(10), false
        ));
        assertFalse(blocked.admitted());
        assertTrue(blocked.rejectReason().contains("already_extracted"));

        AdmissionController.AdmissionDecision forced = admission.admit(new AdmissionController.SubmitAiJobCommand(
                tenant, meeting, transcript, "CHUNK_EXTRACTION", JobPriority.NORMAL,
                AiCapability.TRANSCRIPT_EXTRACTION, "pv", "sv", "tr", 10, null,
                UUID.randomUUID(), now.plusSeconds(20), true
        ));
        assertTrue(forced.admitted());
    }

    private static TenantAiPolicyPort permissivePolicy() {
        return new TenantAiPolicyPort() {
            @Override
            public boolean isModelAllowed(UUID tenantId, String modelKey) {
                return true;
            }

            @Override
            public boolean isCriticalFallbackAllowed(UUID tenantId) {
                return true;
            }

            @Override
            public int maxConcurrentAiJobs(UUID tenantId) {
                return 8;
            }

            @Override
            public Duration slaTarget(UUID tenantId, JobPriority priority) {
                return Duration.ofHours(1);
            }

            @Override
            public Set<String> allowedModelKeys(UUID tenantId) {
                return Set.of("local");
            }
        };
    }

    private static AdmissionController.SubmitAiJobCommand command(
            UUID tenant,
            UUID meeting,
            UUID transcript,
            Instant now
    ) {
        return new AdmissionController.SubmitAiJobCommand(
                tenant, meeting, transcript, "CHUNK_EXTRACTION", JobPriority.NORMAL,
                AiCapability.TRANSCRIPT_EXTRACTION, "pv", "sv", "tr", 10, null,
                UUID.randomUUID(), now, false);
    }

    private static ModelRouter successRouter() {
        return request -> ModelRouter.RouteResult.success(
                new SelectedRoute(
                        UUID.fromString("00000000-0000-4000-8000-000000000001"),
                        UUID.fromString("00000000-0000-4000-8000-000000000002"),
                        "local",
                        "test",
                        List.of(),
                        request.now()
                ),
                List.of()
        );
    }
}
