package com.nanobaseai.actenora.policy.faz28;

import com.nanobaseai.actenora.policy.api.QuotaProblemDetails;
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

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FAZ 28: tenant quota exceeded under load.
 */
class QuotaUnderLoadScenarioTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-25T12:00:00Z"), ZoneOffset.UTC);

    private PolicyEvaluationService service;
    private InMemoryQuotaUsageStore usage;
    private TenantId tenant;

    @BeforeEach
    void setUp() {
        InMemoryTenantPolicyRepository repository = new InMemoryTenantPolicyRepository();
        InMemoryPolicyCache cache = new InMemoryPolicyCache();
        usage = new InMemoryQuotaUsageStore();
        service = new PolicyEvaluationService(repository, cache, usage, CLOCK);
        tenant = TenantId.of(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"));
        service.saveOverride(TenantPolicyOverride.builder(tenant)
                .quotas(new QuotaLimits(30, 100, 1000, 1000, 4, 60, 1_000_000))
                .build());
    }

    @Test
    void dailyMeetingQuota_blocksThirtyFirstUnderLoad() {
        LocalDate day = LocalDate.now(CLOCK);
        for (int i = 0; i < 30; i++) {
            service.assertWithinQuota(tenant, QuotaDimension.DAILY_MEETING, 1);
            usage.addUsage(tenant, QuotaDimension.DAILY_MEETING, day, 1);
        }

        QuotaExceededException ex = assertThrows(
                QuotaExceededException.class,
                () -> service.assertWithinQuota(tenant, QuotaDimension.DAILY_MEETING, 1)
        );
        QuotaProblemDetails problem = QuotaProblemDetails.from(ex, URI.create("/api/v1/meetings"));
        assertEquals(429, problem.status());
        assertTrue(problem.toJson().contains("DAILY_MEETING"));
        assertEquals(30L, problem.extensions().get("limit"));
        assertEquals(30L, problem.extensions().get("used"));
    }
}
