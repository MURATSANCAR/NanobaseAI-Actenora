package com.nanobaseai.actenora.aiprocessing.application.scheduling;

import com.nanobaseai.actenora.aiprocessing.application.port.TenantAiPolicyPort;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.JobPriority;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records SLA breaches for load / resilience reporting (FAZ 28).
 */
public final class SlaBreachTracker {

    private final TenantAiPolicyPort policy;
    private final CopyOnWriteArrayList<Breach> breaches = new CopyOnWriteArrayList<>();

    public SlaBreachTracker(TenantAiPolicyPort policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public boolean recordIfBreached(AiJob job, Instant completedAt) {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(completedAt, "completedAt");
        Duration target = policy.slaTarget(job.tenantId(), job.priority());
        Duration elapsed = Duration.between(job.queuedAt(), completedAt);
        if (elapsed.compareTo(target) > 0) {
            Breach breach = new Breach(
                    job.id(),
                    job.tenantId(),
                    job.priority(),
                    target,
                    elapsed,
                    completedAt
            );
            breaches.add(breach);
            return true;
        }
        return false;
    }

    public int breachCount() {
        return breaches.size();
    }

    public int breachCount(JobPriority priority) {
        Objects.requireNonNull(priority, "priority");
        return (int) breaches.stream().filter(b -> b.priority() == priority).count();
    }

    public List<Breach> breaches() {
        return List.copyOf(breaches);
    }

    public void clear() {
        breaches.clear();
    }

    public record Breach(
            UUID jobId,
            UUID tenantId,
            JobPriority priority,
            Duration target,
            Duration elapsed,
            Instant observedAt
    ) {
        public Breach {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(priority, "priority");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(elapsed, "elapsed");
            Objects.requireNonNull(observedAt, "observedAt");
        }

        public List<String> asReportRow() {
            List<String> row = new ArrayList<>(6);
            row.add(jobId.toString());
            row.add(tenantId.toString());
            row.add(priority.name());
            row.add(target.toString());
            row.add(elapsed.toString());
            row.add(observedAt.toString());
            return List.copyOf(row);
        }
    }
}
