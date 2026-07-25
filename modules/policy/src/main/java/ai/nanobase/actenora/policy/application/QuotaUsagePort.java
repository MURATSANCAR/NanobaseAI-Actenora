package ai.nanobase.actenora.policy.application;

import ai.nanobase.actenora.policy.domain.QuotaDimension;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Tracks quota consumption. Durable store is source of truth for counters.
 */
public interface QuotaUsagePort {

    long getUsage(UUID tenantId, QuotaDimension dimension, LocalDate day);

    void addUsage(UUID tenantId, QuotaDimension dimension, LocalDate day, long amount);

    int getConcurrentAiJobs(UUID tenantId);

    void setConcurrentAiJobs(UUID tenantId, int count);
}
