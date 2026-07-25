package com.nanobaseai.actenora.security.policy;

import com.nanobaseai.actenora.aiprocessing.application.port.TenantAiPolicyPort;
import com.nanobaseai.actenora.aiprocessing.domain.job.JobPriority;
import com.nanobaseai.actenora.audit.api.AuditApi;
import com.nanobaseai.actenora.meeting.application.port.MeetingAuditPort;
import com.nanobaseai.actenora.meeting.application.port.MeetingQuotaPort;
import com.nanobaseai.actenora.modelmanagement.application.TenantModelAllowlistPort;
import com.nanobaseai.actenora.policy.api.PolicyApi;
import com.nanobaseai.actenora.policy.application.QuotaUsagePort;
import com.nanobaseai.actenora.policy.domain.QuotaDimension;
import com.nanobaseai.actenora.policy.domain.SlaLevel;
import com.nanobaseai.actenora.policy.domain.TenantPolicy;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * FAZ 5 — bind Policy/Audit façades into module ports without cross-module repository access.
 */
@Configuration
public class PolicyPlatformConfiguration {

    @Bean
    @Primary
    TenantModelAllowlistPort policyBackedTenantModelAllowlist(PolicyApi policyApi) {
        return (tenantId, modelKey) -> policyApi.isModelAllowed(TenantId.of(tenantId), modelKey);
    }

    @Bean
    @Primary
    TenantAiPolicyPort policyBackedTenantAiPolicy(PolicyApi policyApi) {
        return new PolicyBackedTenantAiPolicy(policyApi);
    }

    @Bean
    @Primary
    MeetingAuditPort auditBackedMeetingAuditPort(AuditApi auditApi) {
        return (tenantId, actorUserId, action, resourceType, resourceId, metadata) ->
                auditApi.append(
                        tenantId.value(),
                        actorUserId == null ? "system" : actorUserId.toString(),
                        action,
                        resourceType,
                        resourceId,
                        metadata == null ? Map.of() : metadata,
                        Instant.now()
                );
    }

    @Bean
    @Primary
    MeetingQuotaPort policyBackedMeetingQuotaPort(MeetingQuotaGuard meetingQuotaGuard) {
        return new MeetingQuotaPort() {
            @Override
            public void assertCanCreateMeeting(TenantId tenantId) {
                meetingQuotaGuard.assertCanCreateMeeting(tenantId);
            }

            @Override
            public void recordMeetingCreated(TenantId tenantId) {
                meetingQuotaGuard.recordMeetingCreated(tenantId);
            }
        };
    }

    @Bean
    MeetingQuotaGuard meetingQuotaGuard(PolicyApi policyApi, QuotaUsagePort quotaUsage, Clock clock) {
        return new MeetingQuotaGuard(policyApi, quotaUsage, clock);
    }

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock platformClock() {
        return Clock.systemUTC();
    }

    public static final class MeetingQuotaGuard {
        private final PolicyApi policyApi;
        private final QuotaUsagePort quotaUsage;
        private final Clock clock;

        public MeetingQuotaGuard(PolicyApi policyApi, QuotaUsagePort quotaUsage, Clock clock) {
            this.policyApi = Objects.requireNonNull(policyApi, "policyApi");
            this.quotaUsage = Objects.requireNonNull(quotaUsage, "quotaUsage");
            this.clock = Objects.requireNonNull(clock, "clock");
        }

        public void assertCanCreateMeeting(TenantId tenantId) {
            policyApi.assertWithinQuota(tenantId, QuotaDimension.DAILY_MEETING, 1);
        }

        public void recordMeetingCreated(TenantId tenantId) {
            quotaUsage.addUsage(tenantId, QuotaDimension.DAILY_MEETING, LocalDate.now(clock), 1);
        }
    }

    public static final class PolicyBackedTenantAiPolicy implements TenantAiPolicyPort {
        private final PolicyApi policyApi;

        public PolicyBackedTenantAiPolicy(PolicyApi policyApi) {
            this.policyApi = Objects.requireNonNull(policyApi, "policyApi");
        }

        @Override
        public boolean isModelAllowed(UUID tenantId, String modelKey) {
            return policyApi.isModelAllowed(TenantId.of(tenantId), modelKey);
        }

        @Override
        public boolean isCriticalFallbackAllowed(UUID tenantId) {
            return policyApi.isCriticalMeetingFallbackAllowed(TenantId.of(tenantId));
        }

        @Override
        public int maxConcurrentAiJobs(UUID tenantId) {
            return policyApi.evaluate(TenantId.of(tenantId)).concurrency().maxConcurrentAiJobs();
        }

        @Override
        public Duration slaTarget(UUID tenantId, JobPriority priority) {
            TenantPolicy policy = policyApi.evaluate(TenantId.of(tenantId));
            SlaLevel level = switch (priority) {
                case CRITICAL -> SlaLevel.CRITICAL;
                case HIGH -> SlaLevel.HIGH;
                case NORMAL -> SlaLevel.NORMAL;
                case BULK -> SlaLevel.BULK;
            };
            SlaLevel resolved = policyApi.resolveSlaLevel(TenantId.of(tenantId), level);
            return Duration.ofMinutes(policy.processingSla().targetMinutes(resolved));
        }

        @Override
        public Set<String> allowedModelKeys(UUID tenantId) {
            return policyApi.evaluate(TenantId.of(tenantId)).modelAccess().allowedModelKeys();
        }
    }
}
