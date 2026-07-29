package com.nanobaseai.actenora.aiprocessing.infrastructure.persistence;

import com.nanobaseai.actenora.aiprocessing.application.port.TenantAiPolicyPort;
import com.nanobaseai.actenora.aiprocessing.domain.job.JobPriority;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class InMemoryTenantAiPolicy implements TenantAiPolicyPort {

    private final Map<UUID, Set<String>> allowlists = new HashMap<>();
    private final Map<UUID, Boolean> criticalFallback = new HashMap<>();
    private final Map<UUID, Integer> concurrencyLimits = new HashMap<>();
    private final Map<JobPriority, Duration> slaTargets = new HashMap<>();

    public InMemoryTenantAiPolicy() {
        // Align with AiJobSla — long meetings need multi-hour admission windows.
        slaTargets.put(JobPriority.CRITICAL, Duration.ofHours(2));
        slaTargets.put(JobPriority.HIGH, Duration.ofHours(24));
        slaTargets.put(JobPriority.NORMAL, Duration.ofHours(24));
        slaTargets.put(JobPriority.BULK, Duration.ofHours(48));
    }

    public void allow(UUID tenantId, String... modelKeys) {
        allowlists.computeIfAbsent(tenantId, id -> new HashSet<>()).addAll(Set.of(modelKeys));
    }

    public void replaceAllowlist(UUID tenantId, String... modelKeys) {
        allowlists.put(tenantId, new HashSet<>(Set.of(modelKeys)));
    }

    public void setCriticalFallbackAllowed(UUID tenantId, boolean allowed) {
        criticalFallback.put(tenantId, allowed);
    }

    public void setMaxConcurrentAiJobs(UUID tenantId, int limit) {
        concurrencyLimits.put(tenantId, limit);
    }

    @Override
    public boolean isModelAllowed(UUID tenantId, String modelKey) {
        Set<String> allowed = allowlists.get(tenantId);
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        return allowed.contains(modelKey);
    }

    @Override
    public boolean isCriticalFallbackAllowed(UUID tenantId) {
        return criticalFallback.getOrDefault(tenantId, false);
    }

    @Override
    public int maxConcurrentAiJobs(UUID tenantId) {
        return concurrencyLimits.getOrDefault(tenantId, 4);
    }

    @Override
    public Duration slaTarget(UUID tenantId, JobPriority priority) {
        Objects.requireNonNull(priority, "priority");
        return slaTargets.getOrDefault(priority, Duration.ofHours(24));
    }

    @Override
    public Set<String> allowedModelKeys(UUID tenantId) {
        return Set.copyOf(allowlists.getOrDefault(tenantId, Set.of()));
    }
}
