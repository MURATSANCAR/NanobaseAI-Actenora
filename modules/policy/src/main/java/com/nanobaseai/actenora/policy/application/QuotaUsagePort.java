package com.nanobaseai.actenora.policy.application;

import com.nanobaseai.actenora.policy.domain.QuotaDimension;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.LocalDate;

/** Tracks quota consumption. Durable store is source of truth for counters. */
public interface QuotaUsagePort {
    long getUsage(TenantId tenantId, QuotaDimension dimension, LocalDate day);
    void addUsage(TenantId tenantId, QuotaDimension dimension, LocalDate day, long amount);
    int getConcurrentAiJobs(TenantId tenantId);
    void setConcurrentAiJobs(TenantId tenantId, int count);
}
