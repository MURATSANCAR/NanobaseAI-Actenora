package com.nanobaseai.actenora.security.aiprocessing;

import com.nanobaseai.actenora.aiprocessing.api.AiProcessingApi;
import com.nanobaseai.actenora.aiprocessing.api.AiProcessingProblemDetails;
import com.nanobaseai.actenora.aiprocessing.api.MultiModelRoutingApi;
import com.nanobaseai.actenora.aiprocessing.api.MultiModelRoutingDtos;
import com.nanobaseai.actenora.aiprocessing.application.port.AdmissionController;
import com.nanobaseai.actenora.aiprocessing.application.port.JobScheduler;
import com.nanobaseai.actenora.aiprocessing.application.port.TenantAiPolicyPort;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiCapability;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJobException;
import com.nanobaseai.actenora.aiprocessing.domain.job.JobPriority;
import com.nanobaseai.actenora.aiprocessing.domain.job.SelectedRoute;
import com.nanobaseai.actenora.aiprocessing.domain.routing.InferenceTaskType;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ValidationModelPreference;
import com.nanobaseai.actenora.identity.api.IdentityApi;
import com.nanobaseai.actenora.identity.api.RequiresPermission;
import com.nanobaseai.actenora.identity.domain.Permission;
import com.nanobaseai.actenora.sharedkernel.security.AuthenticatedPrincipal;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Auth-bound AI job + multi-model routing surface. Tenant id from {@link TenantSecurityContext} only.
 */
@RestController
@RequestMapping("/api/v1")
public class AiProcessingController {

    private final AiProcessingApi aiProcessingApi;
    private final MultiModelRoutingApi multiModelRoutingApi;
    private final TenantAiPolicyPort tenantAiPolicy;
    private final IdentityApi identityApi;

    public AiProcessingController(
            AiProcessingApi aiProcessingApi,
            MultiModelRoutingApi multiModelRoutingApi,
            TenantAiPolicyPort tenantAiPolicy,
            IdentityApi identityApi
    ) {
        this.aiProcessingApi = Objects.requireNonNull(aiProcessingApi, "aiProcessingApi");
        this.multiModelRoutingApi = Objects.requireNonNull(multiModelRoutingApi, "multiModelRoutingApi");
        this.tenantAiPolicy = Objects.requireNonNull(tenantAiPolicy, "tenantAiPolicy");
        this.identityApi = Objects.requireNonNull(identityApi, "identityApi");
    }

    @PostMapping("/ai-jobs")
    @RequiresPermission(Permission.MEETING_WRITE)
    public ResponseEntity<AdmissionResponse> submit(@RequestBody SubmitAiJobRequest body) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_WRITE);
        Instant now = Instant.now();
        AdmissionController.SubmitAiJobCommand command = new AdmissionController.SubmitAiJobCommand(
                principal.tenantId().value(),
                body.meetingOccurrenceId(),
                body.transcriptId(),
                body.taskType(),
                body.priority() == null ? JobPriority.NORMAL : body.priority(),
                body.requestedCapability(),
                body.promptVersion(),
                body.schemaVersion(),
                body.language(),
                body.contextSize(),
                body.fallbackPermittedOverride(),
                body.correlationId() == null ? UUID.randomUUID() : body.correlationId(),
                now
        );
        AdmissionController.AdmissionDecision decision = aiProcessingApi.submitJob(command);
        if (!decision.admitted()) {
            throw AiJobException.admissionRejected(decision.rejectReason());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(AdmissionResponse.from(decision));
    }

    @GetMapping("/ai-jobs/{jobId}")
    @RequiresPermission(Permission.MEETING_READ)
    public AiJobView find(@PathVariable UUID jobId) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_READ);
        AiJob job = aiProcessingApi.findJob(jobId)
                .orElseThrow(() -> AiJobException.notFound("Job not found: " + jobId));
        assertSameTenant(principal, job.tenantId());
        return AiJobView.from(job);
    }

    @PostMapping("/ai-jobs/{jobId}/cancel")
    @RequiresPermission(Permission.MEETING_WRITE)
    public AiJobView cancel(@PathVariable UUID jobId) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_WRITE);
        AiJob existing = aiProcessingApi.findJob(jobId)
                .orElseThrow(() -> AiJobException.notFound("Job not found: " + jobId));
        assertSameTenant(principal, existing.tenantId());
        return AiJobView.from(aiProcessingApi.cancelJob(jobId, Instant.now()));
    }

    @PostMapping("/ai-jobs/claim-next")
    @RequiresPermission(Permission.OPERATIONS_MANAGE)
    public ResponseEntity<ClaimedJobView> claimNext() {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.OPERATIONS_MANAGE);
        Optional<JobScheduler.ClaimedJob> claimed = aiProcessingApi.claimNext(Instant.now());
        return claimed
                .map(c -> ResponseEntity.ok(ClaimedJobView.from(c)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/ai-jobs/{jobId}/admin-override")
    @RequiresPermission(Permission.MODEL_CONTROL)
    public AiJobView adminOverride(@PathVariable UUID jobId, @RequestBody AdminOverrideRequest body) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MODEL_CONTROL);
        AiJob existing = aiProcessingApi.findJob(jobId)
                .orElseThrow(() -> AiJobException.notFound("Job not found: " + jobId));
        assertSameTenant(principal, existing.tenantId());
        return AiJobView.from(aiProcessingApi.adminOverrideRoute(
                jobId,
                body.modelDefinitionId(),
                body.deploymentId(),
                body.modelKey(),
                true,
                Instant.now()
        ));
    }

    @PostMapping("/ai-routing/route")
    @RequiresPermission(Permission.OPERATIONS_MANAGE)
    public MultiModelRoutingDtos.RoutingDecisionView route(@RequestBody RouteRequest body) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.OPERATIONS_MANAGE);
        UUID tenantId = principal.tenantId().value();
        Set<String> allowlist = tenantAiPolicy.allowedModelKeys(tenantId);
        MultiModelRoutingDtos.RouteJobCommand command = new MultiModelRoutingDtos.RouteJobCommand(
                body.jobId(),
                tenantId,
                body.taskType(),
                body.critical(),
                body.correlationId() == null ? UUID.randomUUID() : body.correlationId(),
                allowlist,
                body.allowQualityDowngrade(),
                !tenantAiPolicy.isCriticalFallbackAllowed(tenantId),
                body.validationModelPreference() == null
                        ? ValidationModelPreference.QWEN27_FINAL
                        : body.validationModelPreference(),
                body.shadowExecutionEnabled()
        );
        return multiModelRoutingApi.routeJob(command);
    }

    @ExceptionHandler(AiJobException.class)
    public ResponseEntity<String> handleAiJob(AiJobException ex, HttpServletRequest request) {
        AiProcessingProblemDetails problem =
                AiProcessingProblemDetails.from(ex, URI.create(request.getRequestURI()));
        return ResponseEntity.status(problem.status())
                .contentType(MediaType.parseMediaType(AiProcessingProblemDetails.MEDIA_TYPE))
                .body(problem.toJson());
    }

    private static void assertSameTenant(AuthenticatedPrincipal principal, UUID jobTenantId) {
        if (!principal.tenantId().value().equals(jobTenantId)) {
            throw AiJobException.forbidden("Cross-tenant AI job access denied");
        }
    }

    public record SubmitAiJobRequest(
            UUID meetingOccurrenceId,
            UUID transcriptId,
            String taskType,
            JobPriority priority,
            AiCapability requestedCapability,
            String promptVersion,
            String schemaVersion,
            String language,
            int contextSize,
            Boolean fallbackPermittedOverride,
            UUID correlationId
    ) {
    }

    public record AdminOverrideRequest(
            UUID modelDefinitionId,
            UUID deploymentId,
            String modelKey
    ) {
    }

    public record RouteRequest(
            UUID jobId,
            InferenceTaskType taskType,
            boolean critical,
            UUID correlationId,
            boolean allowQualityDowngrade,
            ValidationModelPreference validationModelPreference,
            boolean shadowExecutionEnabled
    ) {
    }

    public record AdmissionResponse(
            boolean admitted,
            AiJobView job,
            long estimatedQueueWaitSeconds,
            String rejectReason
    ) {
        static AdmissionResponse from(AdmissionController.AdmissionDecision decision) {
            Duration wait = decision.estimatedQueueWait() == null
                    ? Duration.ZERO
                    : decision.estimatedQueueWait();
            return new AdmissionResponse(
                    decision.admitted(),
                    decision.job() == null ? null : AiJobView.from(decision.job()),
                    wait.toSeconds(),
                    decision.rejectReason()
            );
        }
    }

    public record ClaimedJobView(AiJobView job, UUID attemptId) {
        static ClaimedJobView from(JobScheduler.ClaimedJob claimed) {
            return new ClaimedJobView(AiJobView.from(claimed.job()), claimed.attempt().id());
        }
    }

    public record AiJobView(
            UUID id,
            UUID tenantId,
            UUID meetingOccurrenceId,
            UUID transcriptId,
            String taskType,
            JobPriority priority,
            String status,
            AiCapability requestedCapability,
            UUID selectedModelId,
            UUID selectedDeploymentId,
            SelectedRouteView selectedRoute,
            String promptVersion,
            String schemaVersion,
            Instant queuedAt,
            Instant startedAt,
            Instant completedAt,
            Instant deadlineAt,
            UUID correlationId,
            String language,
            int contextSize,
            boolean fallbackPermitted,
            int attemptCount
    ) {
        static AiJobView from(AiJob job) {
            return new AiJobView(
                    job.id(),
                    job.tenantId(),
                    job.meetingOccurrenceId(),
                    job.transcriptId(),
                    job.taskType(),
                    job.priority(),
                    job.status().name(),
                    job.requestedCapability(),
                    job.selectedModelId().orElse(null),
                    job.selectedDeploymentId().orElse(null),
                    job.selectedRoute().map(SelectedRouteView::from).orElse(null),
                    job.promptVersion(),
                    job.schemaVersion(),
                    job.queuedAt(),
                    job.startedAt().orElse(null),
                    job.completedAt().orElse(null),
                    job.deadlineAt(),
                    job.correlationId(),
                    job.language(),
                    job.contextSize(),
                    job.fallbackPermitted(),
                    job.attemptCount()
            );
        }
    }

    public record SelectedRouteView(
            UUID modelDefinitionId,
            UUID deploymentId,
            String modelKey,
            String reason,
            List<String> rejectReasons,
            Instant selectedAt
    ) {
        static SelectedRouteView from(SelectedRoute route) {
            return new SelectedRouteView(
                    route.modelDefinitionId(),
                    route.deploymentId(),
                    route.modelKey(),
                    route.reason(),
                    route.rejectReasons(),
                    route.selectedAt()
            );
        }
    }
}
