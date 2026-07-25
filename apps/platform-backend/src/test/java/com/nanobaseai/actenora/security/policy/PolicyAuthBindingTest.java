package com.nanobaseai.actenora.security.policy;

import com.nanobaseai.actenora.audit.api.AuditApi;
import com.nanobaseai.actenora.audit.application.AuditAppendService;
import com.nanobaseai.actenora.audit.infrastructure.AuditApiAdapter;
import com.nanobaseai.actenora.audit.infrastructure.InMemoryAuditEntryStore;
import com.nanobaseai.actenora.policy.api.PolicyApi;
import com.nanobaseai.actenora.policy.application.PolicyEvaluationService;
import com.nanobaseai.actenora.policy.domain.QuotaDimension;
import com.nanobaseai.actenora.policy.domain.QuotaExceededException;
import com.nanobaseai.actenora.policy.domain.QuotaLimits;
import com.nanobaseai.actenora.policy.domain.TenantPolicyOverride;
import com.nanobaseai.actenora.policy.infrastructure.cache.InMemoryPolicyCache;
import com.nanobaseai.actenora.policy.infrastructure.persistence.InMemoryQuotaUsageStore;
import com.nanobaseai.actenora.policy.infrastructure.persistence.InMemoryTenantPolicyRepository;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyAuthBindingTest {

    private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");

    private PolicyApi policyApi;
    private InMemoryQuotaUsageStore usage;
    private InMemoryPolicyCache cache;
    private InMemoryTenantPolicyRepository repository;
    private AuditApi auditApi;
    private PolicyPlatformConfiguration.MeetingQuotaGuard meetingQuotaGuard;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        repository = new InMemoryTenantPolicyRepository();
        cache = new InMemoryPolicyCache();
        usage = new InMemoryQuotaUsageStore();
        policyApi = new PolicyEvaluationService(repository, cache, usage, clock);
        auditApi = new AuditApiAdapter(new AuditAppendService(new InMemoryAuditEntryStore()));
        meetingQuotaGuard = new PolicyPlatformConfiguration.MeetingQuotaGuard(policyApi, usage, clock);
    }

    @Test
    void quotaExceededOnMeetingCreate() {
        TenantId tenantId = TenantId.random();
        policyApi.saveOverride(TenantPolicyOverride.builder(tenantId)
                .quotas(new QuotaLimits(1, 100, 1000, 1000, 2, 60, 1_000_000))
                .build());
        meetingQuotaGuard.assertCanCreateMeeting(tenantId);
        meetingQuotaGuard.recordMeetingCreated(tenantId);

        assertThrows(QuotaExceededException.class, () -> meetingQuotaGuard.assertCanCreateMeeting(tenantId));
    }

    @Test
    void cacheLossStillResolvesFromSourceOfTruth() {
        TenantId tenantId = TenantId.random();
        policyApi.saveOverride(TenantPolicyOverride.builder(tenantId)
                .quotas(new QuotaLimits(3, 100, 1000, 1000, 2, 60, 1_000_000))
                .build());
        assertEquals(3, policyApi.evaluate(tenantId).quotas().dailyMeetingLimit());
        cache.evict(tenantId);
        assertEquals(3, policyApi.evaluate(tenantId).quotas().dailyMeetingLimit());
    }

    @Test
    void modelAllowlistAndCriticalFallbackBoundToPolicy() {
        TenantId tenantId = TenantId.random();
        PolicyPlatformConfiguration.PolicyBackedTenantAiPolicy ai =
                new PolicyPlatformConfiguration.PolicyBackedTenantAiPolicy(policyApi);
        assertTrue(ai.isModelAllowed(tenantId.value(), "local-default"));
        assertTrue(ai.isCriticalFallbackAllowed(tenantId.value()));

        policyApi.saveOverride(TenantPolicyOverride.builder(tenantId)
                .modelAccess(new com.nanobaseai.actenora.policy.domain.ModelAccessPolicy(
                        java.util.Set.of("qwen-local"), false))
                .build());
        assertTrue(ai.isModelAllowed(tenantId.value(), "qwen-local"));
        assertFalse(ai.isModelAllowed(tenantId.value(), "local-default"));
        assertFalse(ai.isCriticalFallbackAllowed(tenantId.value()));
    }

    @Test
    void auditSanitizesForbiddenTranscriptMetadata() {
        UUID tenantId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        auditApi.append(
                tenantId,
                "actor",
                "NOTE_VIEWED",
                "MeetingNote",
                resourceId,
                Map.of("transcript", "secret words", "ok", "visible"),
                NOW
        );
        var timeline = auditApi.timeline(tenantId, resourceId);
        assertEquals(1, timeline.size());
        assertFalse(timeline.getFirst().metadata().containsKey("transcript"));
        assertEquals("visible", timeline.getFirst().metadata().get("ok"));
    }

    @Test
    void concurrencyLimitEnforced() {
        TenantId tenantId = TenantId.random();
        policyApi.saveOverride(TenantPolicyOverride.builder(tenantId)
                .quotas(new QuotaLimits(50, 100, 1000, 1000, 1, 60, 1_000_000))
                .concurrency(new com.nanobaseai.actenora.policy.domain.ConcurrencyPolicy(1, 2))
                .build());
        usage.setConcurrentAiJobs(tenantId, 1);
        assertThrows(QuotaExceededException.class, () -> policyApi.assertConcurrencyAvailable(tenantId));
    }

    @Test
    void tenantIsolationOnQuotaUsage() {
        TenantId a = TenantId.random();
        TenantId b = TenantId.random();
        usage.addUsage(a, QuotaDimension.DAILY_MEETING, LocalDate.now(ZoneOffset.UTC), 10);
        assertEquals(10, usage.getUsage(a, QuotaDimension.DAILY_MEETING, LocalDate.now(ZoneOffset.UTC)));
        assertEquals(0, usage.getUsage(b, QuotaDimension.DAILY_MEETING, LocalDate.now(ZoneOffset.UTC)));
    }
}
