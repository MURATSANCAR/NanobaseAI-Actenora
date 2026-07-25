package com.nanobaseai.actenora.policy.application;

import com.nanobaseai.actenora.policy.api.PolicyApi;
import com.nanobaseai.actenora.policy.domain.PolicyEvaluator;
import com.nanobaseai.actenora.policy.domain.QuotaDimension;
import com.nanobaseai.actenora.policy.domain.QuotaExceededException;
import com.nanobaseai.actenora.policy.domain.SlaLevel;
import com.nanobaseai.actenora.policy.domain.TenantPolicy;
import com.nanobaseai.actenora.policy.domain.TenantPolicyOverride;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Application service implementing published policy evaluation.
 * Cache is optional; repository (PostgreSQL) is always the source of truth.
 */
public final class PolicyEvaluationService implements PolicyApi {

    private final TenantPolicyRepositoryPort repository;
    private final PolicyCachePort cache;
    private final QuotaUsagePort quotaUsage;
    private final PolicyEvaluator evaluator;
    private final Clock clock;

    public PolicyEvaluationService(
            TenantPolicyRepositoryPort repository,
            PolicyCachePort cache,
            QuotaUsagePort quotaUsage,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.quotaUsage = Objects.requireNonNull(quotaUsage, "quotaUsage");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.evaluator = new PolicyEvaluator();
    }

    @Override
    public TenantPolicy evaluate(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        return cache.get(tenantId).orElseGet(() -> {
            TenantPolicy resolved = resolveFromSourceOfTruth(tenantId);
            cache.put(tenantId, resolved);
            return resolved;
        });
    }

    @Override
    public void saveOverride(TenantPolicyOverride override) {
        Objects.requireNonNull(override, "override");
        repository.saveOverride(override);
        TenantPolicy effective = resolveFromSourceOfTruth(override.tenantId());
        repository.saveMaterialized(effective);
        cache.put(override.tenantId(), effective);
    }

    @Override
    public void assertWithinQuota(TenantId tenantId, QuotaDimension dimension, long requestedAmount)
            throws QuotaExceededException {
        TenantPolicy policy = evaluate(tenantId);
        LocalDate day = LocalDate.now(clock);
        long used = switch (dimension) {
            case CONCURRENT_AI_JOBS -> quotaUsage.getConcurrentAiJobs(tenantId);
            default -> quotaUsage.getUsage(tenantId, dimension, day);
        };
        evaluator.assertWithinQuota(policy, dimension, used, requestedAmount);
    }

    @Override
    public void assertConcurrencyAvailable(TenantId tenantId) throws QuotaExceededException {
        TenantPolicy policy = evaluate(tenantId);
        evaluator.assertConcurrencyAvailable(policy, quotaUsage.getConcurrentAiJobs(tenantId));
    }

    @Override
    public boolean isModelAllowed(TenantId tenantId, String modelKey) {
        return evaluator.isModelAllowed(evaluate(tenantId), modelKey);
    }

    @Override
    public boolean isCriticalMeetingFallbackAllowed(TenantId tenantId) {
        return evaluator.isCriticalMeetingFallbackAllowed(evaluate(tenantId));
    }

    @Override
    public SlaLevel resolveSlaLevel(TenantId tenantId, SlaLevel requestedOrNull) {
        return evaluator.resolveSlaLevel(evaluate(tenantId), requestedOrNull);
    }

    /** Forces a reload from PostgreSQL-backed repository, ignoring cache. */
    public TenantPolicy reloadFromSourceOfTruth(TenantId tenantId) {
        cache.evict(tenantId);
        return evaluate(tenantId);
    }

    private TenantPolicy resolveFromSourceOfTruth(TenantId tenantId) {
        return evaluator.resolve(tenantId, repository.findOverride(tenantId).orElse(null));
    }
}
