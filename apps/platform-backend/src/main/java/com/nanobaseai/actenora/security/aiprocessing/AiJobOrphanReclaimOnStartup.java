package com.nanobaseai.actenora.security.aiprocessing;

import com.nanobaseai.actenora.aiprocessing.api.AiProcessingApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

/**
 * Requeues RUNNING AI jobs whose in-process work was lost on JVM/container restart.
 *
 * <p>Periodic stale recovery uses a long grace ({@code stale-running-after}, default 24h) so
 * multi-hour legitimate pipelines are not killed. That same timer leaves crash orphans stuck
 * until cancel or day-long reclaim. On single-instance deploys, reclaiming RUNNING jobs that
 * started <em>before this process</em> is safe.
 *
 * <p>Jobs claimed after this bean is constructed are left alone, so startup reclaim cannot
 * race with the first post-boot {@code claimNext}.
 *
 * <p>Disable with {@code ACTENORA_AI_WORKER_RECLAIM_ORPHANS_ON_STARTUP=false} when multiple
 * backend replicas share the job table (needs lease/heartbeat ownership first).
 */
@Component
@ConditionalOnProperty(name = "actenora.ai.worker.enabled", havingValue = "true", matchIfMissing = true)
public final class AiJobOrphanReclaimOnStartup {

    private static final Logger log = LoggerFactory.getLogger(AiJobOrphanReclaimOnStartup.class);

    private final AiProcessingApi aiProcessingApi;
    private final int maxAttempts;
    private final boolean reclaimOnStartup;
    /** Exclusive upper bound: only jobs started before this Instant are previous-process orphans. */
    private final Instant processEpoch;

    public AiJobOrphanReclaimOnStartup(
            AiProcessingApi aiProcessingApi,
            @Value("${actenora.ai.provider.max-attempts:5}") int maxAttempts,
            @Value("${actenora.ai.worker.reclaim-orphans-on-startup:true}") boolean reclaimOnStartup
    ) {
        this.aiProcessingApi = Objects.requireNonNull(aiProcessingApi, "aiProcessingApi");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        this.maxAttempts = maxAttempts;
        this.reclaimOnStartup = reclaimOnStartup;
        this.processEpoch = Instant.now();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reclaimOrphans() {
        if (!reclaimOnStartup) {
            log.info("AI worker startup orphan reclaim disabled");
            return;
        }
        Instant now = Instant.now();
        int recovered = aiProcessingApi.recoverRunningStartedBefore(now, processEpoch, maxAttempts);
        if (recovered > 0) {
            log.warn(
                    "Reclaimed {} orphaned RUNNING AI job(s) after startup (started before processEpoch={})",
                    recovered,
                    processEpoch);
        } else {
            log.info("AI worker startup orphan reclaim: no pre-boot RUNNING jobs to requeue");
        }
    }
}
