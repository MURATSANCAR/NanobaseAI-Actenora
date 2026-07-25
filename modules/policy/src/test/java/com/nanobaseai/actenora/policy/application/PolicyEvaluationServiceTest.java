package com.nanobaseai.actenora.policy.application;

import com.nanobaseai.actenora.policy.api.QuotaProblemDetails;
import com.nanobaseai.actenora.policy.domain.ConcurrencyPolicy;
import com.nanobaseai.actenora.policy.domain.ModelAccessPolicy;
import com.nanobaseai.actenora.policy.domain.QuotaDimension;
import com.nanobaseai.actenora.policy.domain.QuotaExceededException;
import com.nanobaseai.actenora.policy.domain.QuotaLimits;
import com.nanobaseai.actenora.policy.domain.RetentionPolicy;
import com.nanobaseai.actenora.policy.domain.SlaLevel;
import com.nanobaseai.actenora.policy.domain.TenantPolicy;
import com.nanobaseai.actenora.policy.domain.TenantPolicyOverride;
import com.nanobaseai.actenora.policy.infrastructure.cache.InMemoryPolicyCache;
import com.nanobaseai.actenora.policy.infrastructure.persistence.InMemoryQuotaUsageStore;
import com.nanobaseai.actenora.policy.infrastructure.persistence.InMemoryTenantPolicyRepository;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyEvaluationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-25T12:00:00Z"), ZoneOffset.UTC);

    private InMemoryTenantPolicyRepository repository;
    private InMemoryPolicyCache cache;
    private InMemoryQuotaUsageStore usage;
    private PolicyEvaluationService service;
    private TenantId tenantA;
    private TenantId tenantB;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTenantPolicyRepository();
        cache = new InMemoryPolicyCache();
        usage = new InMemoryQuotaUsageStore();
        service = new PolicyEvaluationService(repository, cache, usage, CLOCK);
        tenantA = TenantId.of(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        tenantB = TenantId.of(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
    }

    @Test
    void policyOverrideReplacesDefaultRetentionAndAllowlist() {
        service.saveOverride(TenantPolicyOverride.builder(tenantA)
                .retention(new RetentionPolicy(90, false))
                .modelAccess(new ModelAccessPolicy(Set.of("local-qwen", "local-llama"), false))
                .build());

        TenantPolicy effective = service.evaluate(tenantA);

        assertEquals(90, effective.retention().retentionDays());
        assertFalse(effective.retention().legalHoldAllowed());
        assertTrue(effective.modelAccess().isModelAllowed("local-qwen"));
        assertFalse(effective.modelAccess().isModelAllowed("local-default"));
        assertFalse(service.isCriticalMeetingFallbackAllowed(tenantA));
        assertEquals(SlaLevel.NORMAL, service.resolveSlaLevel(tenantA, null));
        assertEquals(SlaLevel.CRITICAL, service.resolveSlaLevel(tenantA, SlaLevel.CRITICAL));
    }

    @Test
    void quotaExceededThrowsAndMapsToRfc7807Problem() {
        service.saveOverride(TenantPolicyOverride.builder(tenantA)
                .quotas(new QuotaLimits(2, 100, 1000, 1000, 2, 60, 1_000_000))
                .build());
        usage.addUsage(tenantA, QuotaDimension.DAILY_MEETING, LocalDate.now(CLOCK), 2);

        QuotaExceededException ex = assertThrows(
                QuotaExceededException.class,
                () -> service.assertWithinQuota(tenantA, QuotaDimension.DAILY_MEETING, 1)
        );

        QuotaProblemDetails problem = QuotaProblemDetails.from(ex, URI.create("/api/v1/meetings"));
        assertEquals(429, problem.status());
        assertEquals("application/problem+json", QuotaProblemDetails.MEDIA_TYPE);
        assertTrue(problem.toJson().contains("\"code\":\"QUOTA_EXCEEDED\""));
        assertTrue(problem.toJson().contains("DAILY_MEETING"));
        assertEquals(2L, problem.extensions().get("limit"));
        assertEquals(2L, problem.extensions().get("used"));
    }

    @Test
    void cacheLossStillResolvesFromSourceOfTruth() {
        service.saveOverride(TenantPolicyOverride.builder(tenantA)
                .retention(new RetentionPolicy(30, true))
                .build());
        assertEquals(30, service.evaluate(tenantA).retention().retentionDays());
        assertEquals(1, cache.size());

        cache.clear();
        assertEquals(0, cache.size());

        TenantPolicy reloaded = service.reloadFromSourceOfTruth(tenantA);
        assertEquals(30, reloaded.retention().retentionDays());
        assertEquals(1, cache.size());
    }

    @Test
    void tenantIsolationKeepsOverridesSeparate() {
        service.saveOverride(TenantPolicyOverride.builder(tenantA)
                .quotas(new QuotaLimits(1, 10, 10, 10, 1, 10, 100))
                .build());
        service.saveOverride(TenantPolicyOverride.builder(tenantB)
                .quotas(new QuotaLimits(100, 10, 10, 10, 1, 10, 100))
                .build());

        assertEquals(1, service.evaluate(tenantA).quotas().dailyMeetingLimit());
        assertEquals(100, service.evaluate(tenantB).quotas().dailyMeetingLimit());

        usage.addUsage(tenantA, QuotaDimension.DAILY_MEETING, LocalDate.now(CLOCK), 1);
        assertThrows(
                QuotaExceededException.class,
                () -> service.assertWithinQuota(tenantA, QuotaDimension.DAILY_MEETING, 1)
        );
        service.assertWithinQuota(tenantB, QuotaDimension.DAILY_MEETING, 1);
    }

    @Test
    void concurrencyLimitEnforcedFromOverride() {
        service.saveOverride(TenantPolicyOverride.builder(tenantA)
                .concurrency(new ConcurrencyPolicy(2, 4))
                .quotas(new QuotaLimits(50, 100, 1000, 1000, 2, 60, 1_000_000))
                .build());
        usage.setConcurrentAiJobs(tenantA, 2);

        QuotaExceededException ex = assertThrows(
                QuotaExceededException.class,
                () -> service.assertConcurrencyAvailable(tenantA)
        );
        assertEquals(QuotaDimension.CONCURRENT_AI_JOBS, ex.dimension());
        assertEquals(2L, ex.limit());
    }

    @Test
    void modelAllowlistRejectsUnknownModels() {
        service.saveOverride(TenantPolicyOverride.builder(tenantA)
                .modelAccess(new ModelAccessPolicy(Set.of("approved-model"), true))
                .build());

        assertTrue(service.isModelAllowed(tenantA, "approved-model"));
        assertFalse(service.isModelAllowed(tenantA, "shadow-model"));
        assertTrue(service.isCriticalMeetingFallbackAllowed(tenantA));
    }
}
