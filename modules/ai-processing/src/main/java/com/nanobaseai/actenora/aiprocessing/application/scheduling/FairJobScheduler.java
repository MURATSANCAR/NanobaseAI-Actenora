package com.nanobaseai.actenora.aiprocessing.application.scheduling;

import com.nanobaseai.actenora.aiprocessing.application.port.AiAttemptRepository;
import com.nanobaseai.actenora.aiprocessing.application.port.AiJobRepository;
import com.nanobaseai.actenora.aiprocessing.application.port.JobScheduler;
import com.nanobaseai.actenora.aiprocessing.application.port.ModelRouter;
import com.nanobaseai.actenora.aiprocessing.application.port.TenantAiPolicyPort;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiAttempt;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJobStatus;
import com.nanobaseai.actenora.aiprocessing.domain.job.JobPriority;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Fair scheduler: priority aging + round-robin among tenants at equal score.
 */
public final class FairJobScheduler implements JobScheduler {

    public static final Duration DEFAULT_AGING_INTERVAL = Duration.ofMinutes(1);
    public static final int DEFAULT_AGING_BONUS = 25;
    public static final Duration DEFAULT_AVG_JOB_DURATION = Duration.ofSeconds(30);

    private final AiJobRepository jobs;
    private final AiAttemptRepository attempts;
    private final TenantAiPolicyPort tenantPolicy;
    private final ModelRouter modelRouter;
    private final Duration agingInterval;
    private final int agingBonusPerInterval;
    private final Duration averageJobDuration;
    private final Map<UUID, Long> tenantFairnessCursor = new HashMap<>();

    public FairJobScheduler(
            AiJobRepository jobs,
            AiAttemptRepository attempts,
            TenantAiPolicyPort tenantPolicy,
            ModelRouter modelRouter
    ) {
        this(jobs, attempts, tenantPolicy, modelRouter, DEFAULT_AGING_INTERVAL, DEFAULT_AGING_BONUS, DEFAULT_AVG_JOB_DURATION);
    }

    public FairJobScheduler(
            AiJobRepository jobs,
            AiAttemptRepository attempts,
            TenantAiPolicyPort tenantPolicy,
            ModelRouter modelRouter,
            Duration agingInterval,
            int agingBonusPerInterval,
            Duration averageJobDuration
    ) {
        this.jobs = Objects.requireNonNull(jobs, "jobs");
        this.attempts = Objects.requireNonNull(attempts, "attempts");
        this.tenantPolicy = Objects.requireNonNull(tenantPolicy, "tenantPolicy");
        this.modelRouter = Objects.requireNonNull(modelRouter, "modelRouter");
        this.agingInterval = Objects.requireNonNull(agingInterval, "agingInterval");
        this.agingBonusPerInterval = agingBonusPerInterval;
        this.averageJobDuration = Objects.requireNonNull(averageJobDuration, "averageJobDuration");
    }

    @Override
    public Optional<ClaimedJob> claimNext(Instant now) {
        Objects.requireNonNull(now, "now");
        List<AiJob> queued = jobs.findQueuedOrdered();
        if (queued.isEmpty()) {
            return Optional.empty();
        }

        List<ScoredJob> scored = queued.stream()
                .map(job -> new ScoredJob(job, job.schedulingScore(now, agingInterval, agingBonusPerInterval)))
                .sorted(this::compareFair)
                .toList();

        for (ScoredJob candidate : scored) {
            AiJob job = candidate.job();
            int running = jobs.countByTenantAndStatus(job.tenantId(), AiJobStatus.RUNNING);
            if (running >= tenantPolicy.maxConcurrentAiJobs(job.tenantId())) {
                continue;
            }

            if (job.selectedRoute().isEmpty()) {
                ModelRouter.RouteResult routed = modelRouter.route(new ModelRouter.RouteRequest(
                        job.tenantId(),
                        job.taskType(),
                        job.requestedCapability(),
                        job.language(),
                        job.contextSize(),
                        job.priority(),
                        effectiveFallback(job),
                        Optional.empty(),
                        now
                ));
                if (!routed.routed()) {
                    continue;
                }
                job.applyRoute(routed.selected());
                jobs.save(job);
            }

            AiAttempt attempt = job.markRunning(now);
            jobs.save(job);
            attempts.save(attempt);
            tenantFairnessCursor.merge(job.tenantId(), 1L, Long::sum);
            return Optional.of(new ClaimedJob(job, attempt));
        }
        return Optional.empty();
    }

    @Override
    public Duration estimateQueueWait(UUID tenantId, JobPriority priority, Instant now) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(now, "now");

        long ahead = jobs.findQueuedOrdered().stream()
                .filter(job -> !job.tenantId().equals(tenantId) || job.priority().baseScore() >= priority.baseScore())
                .filter(job -> {
                    long score = job.schedulingScore(now, agingInterval, agingBonusPerInterval);
                    long probe = priority.baseScore();
                    return score >= probe;
                })
                .count();

        int tenantLimit = Math.max(1, tenantPolicy.maxConcurrentAiJobs(tenantId));
        long batches = (ahead + tenantLimit) / tenantLimit;
        return averageJobDuration.multipliedBy(Math.max(1, batches));
    }

    @Override
    public int recoverStaleRunning(Instant now, Duration staleAfter) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(staleAfter, "staleAfter");
        int recovered = 0;
        for (AiJob job : jobs.findByStatus(AiJobStatus.RUNNING)) {
            Instant started = job.startedAt().orElse(null);
            if (started == null) {
                continue;
            }
            if (!started.plus(staleAfter).isAfter(now)) {
                attempts.findActiveByJobId(job.id()).ifPresent(a -> {
                    a.cancel(now);
                    attempts.save(a);
                });
                job.recoverStale(now);
                jobs.save(job);
                recovered++;
            }
        }
        return recovered;
    }

    private int compareFair(ScoredJob a, ScoredJob b) {
        int byScore = Long.compare(b.score(), a.score());
        if (byScore != 0) {
            return byScore;
        }
        long aServed = tenantFairnessCursor.getOrDefault(a.job().tenantId(), 0L);
        long bServed = tenantFairnessCursor.getOrDefault(b.job().tenantId(), 0L);
        int byFairness = Long.compare(aServed, bServed);
        if (byFairness != 0) {
            return byFairness;
        }
        return a.job().queuedAt().compareTo(b.job().queuedAt());
    }

    private boolean effectiveFallback(AiJob job) {
        if (!job.priority().isCritical()) {
            return job.fallbackPermitted();
        }
        return job.fallbackPermitted() && tenantPolicy.isCriticalFallbackAllowed(job.tenantId());
    }

    private record ScoredJob(AiJob job, long score) {
    }
}
