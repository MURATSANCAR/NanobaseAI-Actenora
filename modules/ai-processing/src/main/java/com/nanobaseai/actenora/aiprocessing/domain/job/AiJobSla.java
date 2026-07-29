package com.nanobaseai.actenora.aiprocessing.domain.job;

import java.time.Duration;
import java.util.Objects;

/**
 * Wall-clock SLAs for AI jobs. Long meetings (multi-hour transcripts) need generous
 * admission deadlines and stale-running grace — a single FINAL_NOTE call can already
 * take 20–40+ minutes on CPU inference.
 */
public final class AiJobSla {

    /** Default when callers omit an explicit deadline (long meetings / staged DAGs). */
    public static final Duration DEFAULT_ADMISSION = Duration.ofHours(24);

    /** Bulk / very large transcript admission window. */
    public static final Duration BULK_ADMISSION = Duration.ofHours(48);

    /**
     * How long a RUNNING job may remain before stale recovery. Must exceed the longest
     * expected end-to-end pipeline for large meetings (not just a single LLM call).
     */
    public static final Duration DEFAULT_STALE_RUNNING = Duration.ofHours(24);

    private AiJobSla() {
    }

    public static Duration admissionDeadline(JobPriority priority) {
        Objects.requireNonNull(priority, "priority");
        return priority == JobPriority.BULK ? BULK_ADMISSION : DEFAULT_ADMISSION;
    }

    /**
     * Scales admission window with transcript size (segment count or context proxy).
     * Extra headroom: +1h per 150 units, capped at +24h.
     */
    public static Duration admissionDeadline(JobPriority priority, int sizeHint) {
        Duration base = admissionDeadline(priority);
        int extraHours = Math.min(24, Math.max(0, sizeHint / 150));
        return base.plus(Duration.ofHours(extraHours));
    }
}
