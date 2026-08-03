package com.nanobaseai.actenora.security.aiprocessing;

import com.nanobaseai.actenora.aiprocessing.api.AiProcessingApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Requeues RUNNING AI jobs whose in-process work was lost on JVM/container restart.
 *
 * <p>Periodic stale recovery uses a long grace ({@code stale-running-after}, default 24h) so
 * multi-hour legitimate pipelines are not killed. That same timer leaves crash orphans stuck
 * until cancel or day-long reclaim. On single-instance deploys, reclaiming all RUNNING jobs at
 * startup is safe: this process is the only worker and cannot resume lost in-memory inference.
 *
 * <p>Disable with {@code ACTENORA_AI_WORKER_RECLAIM_ORPHANS_ON_STARTUP=false} when multiple
 * backend replicas share the job table (needs lease/heartbeat ownership first).
 */
@Component
@ConditionalOnProperty(name = "actenora.ai.worker.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(
        name = "actenora.ai.worker.reclaim-orphans-on-startup",
        havingValue = "true",
        matchIfMissing = true)
public final class AiJobOrphanReclaimOnStartup {

    private static final Logger log = LoggerFactory.getLogger(AiJobOrphanReclaimOnStartup.class);

    private final AiProcessingApi aiProcessingApi;
    private final int maxAttempts;

    public AiJobOrphanReclaimOnStartup(
            AiProcessingApi aiProcessingApi,
            @Value("${actenora.ai.local-provider.max-attempts:3}") int maxAttempts
    ) {
        this.aiProcessingApi = Objects.requireNonNull(aiProcessingApi, "aiProcessingApi");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        this.maxAttempts = maxAttempts;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reclaimOrphans() {
        Instant now = Instant.now();
        // Duration.ZERO ⇒ every RUNNING job with started_at <= now is stale (all crash orphans).
        int recovered = aiProcessingApi.recoverStaleRunning(now, Duration.ZERO, maxAttempts);
        if (recovered > 0) {
            log.warn(
                    "Reclaimed {} orphaned RUNNING AI job(s) after startup (in-memory work lost on restart)",
                    recovered);
        } else {
            log.info("AI worker startup orphan reclaim: no RUNNING jobs to requeue");
        }
    }
}
