package com.nanobaseai.actenora.aiprocessing.application.routing;

import com.nanobaseai.actenora.aiprocessing.application.port.ModelCatalogPort;
import com.nanobaseai.actenora.aiprocessing.application.port.ModelRouter;
import com.nanobaseai.actenora.aiprocessing.application.port.RoutableCandidate;
import com.nanobaseai.actenora.aiprocessing.application.port.TenantAiPolicyPort;
import com.nanobaseai.actenora.aiprocessing.domain.job.SelectedRoute;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Selects the best healthy, allowed, capacity-available local model for a job.
 */
public final class CapabilityModelRouter implements ModelRouter {

    private final ModelCatalogPort catalog;
    private final TenantAiPolicyPort tenantPolicy;

    public CapabilityModelRouter(ModelCatalogPort catalog, TenantAiPolicyPort tenantPolicy) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.tenantPolicy = Objects.requireNonNull(tenantPolicy, "tenantPolicy");
    }

    @Override
    public RouteResult route(RouteRequest request) {
        Objects.requireNonNull(request, "request");
        List<String> rejects = new ArrayList<>();
        List<RoutableCandidate> candidates = catalog.findCandidates(request.requestedCapability());

        List<Scored> eligible = new ArrayList<>();
        for (RoutableCandidate candidate : candidates) {
            Optional<String> reject = rejectReason(request, candidate);
            if (reject.isPresent()) {
                rejects.add(candidate.modelKey() + "/" + candidate.deploymentKey() + ": " + reject.get());
                continue;
            }
            eligible.add(new Scored(candidate, score(candidate)));
        }

        if (eligible.isEmpty()) {
            return RouteResult.failure(inferFailureCode(rejects), rejects);
        }

        eligible.sort(Comparator
                .comparingDouble(Scored::score).reversed()
                .thenComparingInt(s -> s.candidate().modelPriority())
                .thenComparing(s -> s.candidate().deploymentKey()));

        Scored best = eligible.getFirst();
        boolean isFallback = request.preferredModelId()
                .map(id -> !id.equals(best.candidate().modelDefinitionId()))
                .orElse(false);

        if (isFallback && request.priority().isCritical() && !request.fallbackPermitted()) {
            rejects.add(best.candidate().modelKey() + ": critical_fallback_forbidden");
            return RouteResult.failure("CRITICAL_FALLBACK_FORBIDDEN", rejects);
        }

        String reason = buildReason(best.candidate(), isFallback, request);
        SelectedRoute selected = new SelectedRoute(
                best.candidate().modelDefinitionId(),
                best.candidate().deploymentId(),
                best.candidate().modelKey(),
                reason,
                rejects,
                request.now()
        );
        return RouteResult.success(selected, rejects);
    }

    private Optional<String> rejectReason(RouteRequest request, RoutableCandidate candidate) {
        if (!candidate.enabledCapabilities().contains(request.requestedCapability())) {
            return Optional.of("capability_missing");
        }
        if (!candidate.modelAcceptsWork()) {
            return Optional.of("model_not_accepting_work");
        }
        if (!candidate.deploymentAcceptsWork() || !candidate.healthy()) {
            return Optional.of("unhealthy");
        }
        if (!candidate.fitsContext(request.contextSize())) {
            return Optional.of("context_too_large");
        }
        if (!candidate.supportsLanguage(request.language())) {
            return Optional.of("language_unsupported");
        }
        if (!tenantPolicy.isModelAllowed(request.tenantId(), candidate.modelKey())) {
            return Optional.of("tenant_disallowed_model");
        }
        if (!candidate.hasCapacity()) {
            return Optional.of("capacity_exhausted");
        }
        return Optional.empty();
    }

    private static double score(RoutableCandidate candidate) {
        double loadPenalty = candidate.queueDepth() * 0.01 + candidate.currentConcurrency() * 0.05;
        return candidate.qualityScore() * 2.0
                + candidate.speedScore()
                + candidate.modelPriority() * 0.01
                - loadPenalty;
    }

    private static String buildReason(RoutableCandidate selected, boolean fallback, RouteRequest request) {
        String base = fallback ? "fallback_best_fit" : "healthy_best_model";
        return base + " capability=" + request.requestedCapability()
                + " model=" + selected.modelKey()
                + " deployment=" + selected.deploymentKey()
                + " taskType=" + request.taskType();
    }

    static String inferFailureCode(List<String> rejects) {
        if (rejects.isEmpty()) {
            return "NO_CANDIDATES";
        }
        if (allContain(rejects, "context_too_large")) {
            return "CONTEXT_TOO_LARGE";
        }
        if (allContain(rejects, "tenant_disallowed_model")) {
            return "TENANT_DISALLOWED_MODEL";
        }
        if (allContain(rejects, "unhealthy") || allContain(rejects, "model_not_accepting_work")) {
            return "NO_HEALTHY_MODEL";
        }
        if (rejects.stream().anyMatch(r -> r.contains("capacity_exhausted"))
                && rejects.stream().allMatch(r ->
                r.contains("capacity_exhausted") || r.contains("unhealthy"))) {
            return "CAPACITY_EXHAUSTED";
        }
        return "ROUTING_FAILED";
    }

    private static boolean allContain(List<String> rejects, String token) {
        return !rejects.isEmpty() && rejects.stream().allMatch(r -> r.contains(token));
    }

    private record Scored(RoutableCandidate candidate, double score) {
    }
}
