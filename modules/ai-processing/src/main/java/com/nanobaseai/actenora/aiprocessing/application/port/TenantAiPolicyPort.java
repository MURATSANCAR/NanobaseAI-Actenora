package com.nanobaseai.actenora.aiprocessing.application.port;

import com.nanobaseai.actenora.aiprocessing.domain.job.JobPriority;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Tenant policy projection for AI admission and routing (backed by policy BC).
 */
public interface TenantAiPolicyPort {

    boolean isModelAllowed(UUID tenantId, String modelKey);

    boolean isCriticalFallbackAllowed(UUID tenantId);

    int maxConcurrentAiJobs(UUID tenantId);

    Duration slaTarget(UUID tenantId, JobPriority priority);

    Set<String> allowedModelKeys(UUID tenantId);
}
