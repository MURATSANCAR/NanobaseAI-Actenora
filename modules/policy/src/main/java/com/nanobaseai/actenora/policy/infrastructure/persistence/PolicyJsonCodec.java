package com.nanobaseai.actenora.policy.infrastructure.persistence;

import com.nanobaseai.actenora.policy.domain.ConcurrencyPolicy;
import com.nanobaseai.actenora.policy.domain.DeliveryPolicy;
import com.nanobaseai.actenora.policy.domain.ExternalParticipantPolicy;
import com.nanobaseai.actenora.policy.domain.ModelAccessPolicy;
import com.nanobaseai.actenora.policy.domain.ProcessingSlaPolicy;
import com.nanobaseai.actenora.policy.domain.QuotaLimits;
import com.nanobaseai.actenora.policy.domain.RetentionPolicy;
import com.nanobaseai.actenora.policy.domain.SlaLevel;
import com.nanobaseai.actenora.policy.domain.TenantPolicy;
import com.nanobaseai.actenora.policy.domain.TenantPolicyOverride;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcJson;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** JSON codec for policy domain snapshots stored in {@code policy.*} JSONB columns (V121). */
public final class PolicyJsonCodec {

    private PolicyJsonCodec() {
    }

    public static String writeRetention(RetentionPolicy policy) {
        return JdbcJson.write(new RetentionDto(policy.retentionDays(), policy.legalHoldAllowed()));
    }

    public static RetentionPolicy readRetention(String json) {
        RetentionDto dto = JdbcJson.read(json, RetentionDto.class);
        return dto == null ? null : new RetentionPolicy(dto.retentionDays(), dto.legalHoldAllowed());
    }

    public static String writeDelivery(DeliveryPolicy policy) {
        return JdbcJson.write(new DeliveryDto(policy.externalDeliveryAllowed(), policy.requireApproval()));
    }

    public static DeliveryPolicy readDelivery(String json) {
        DeliveryDto dto = JdbcJson.read(json, DeliveryDto.class);
        return dto == null ? null : new DeliveryPolicy(dto.externalDeliveryAllowed(), dto.requireApproval());
    }

    public static String writeModelAccess(ModelAccessPolicy policy) {
        return JdbcJson.write(new ModelAccessDto(
                new LinkedHashSet<>(policy.allowedModelKeys()),
                policy.criticalMeetingFallbackAllowed()
        ));
    }

    public static ModelAccessPolicy readModelAccess(String json) {
        ModelAccessDto dto = JdbcJson.read(json, ModelAccessDto.class);
        return dto == null ? null : new ModelAccessPolicy(dto.allowedModelKeys(), dto.criticalMeetingFallbackAllowed());
    }

    public static String writeProcessingSla(ProcessingSlaPolicy policy) {
        return JdbcJson.write(new ProcessingSlaDto(
                policy.defaultLevel().name(),
                toStringKeyedLatency(policy.targetLatencyMinutes())
        ));
    }

    public static ProcessingSlaPolicy readProcessingSla(String json) {
        ProcessingSlaDto dto = JdbcJson.read(json, ProcessingSlaDto.class);
        if (dto == null) {
            return null;
        }
        EnumMap<SlaLevel, Integer> latency = new EnumMap<>(SlaLevel.class);
        dto.targetLatencyMinutes().forEach((k, v) -> latency.put(SlaLevel.valueOf(k), v));
        return new ProcessingSlaPolicy(SlaLevel.valueOf(dto.defaultLevel()), latency);
    }

    public static String writeConcurrency(ConcurrencyPolicy policy) {
        return JdbcJson.write(new ConcurrencyDto(policy.maxConcurrentAiJobs(), policy.maxConcurrentTranscriptJobs()));
    }

    public static ConcurrencyPolicy readConcurrency(String json) {
        ConcurrencyDto dto = JdbcJson.read(json, ConcurrencyDto.class);
        return dto == null ? null : new ConcurrencyPolicy(dto.maxConcurrentAiJobs(), dto.maxConcurrentTranscriptJobs());
    }

    public static String writeExternalParticipant(ExternalParticipantPolicy policy) {
        return JdbcJson.write(new ExternalParticipantDto(
                policy.externalParticipantsAllowed(),
                policy.redactExternalContent(),
                policy.requireOrganizerApproval()
        ));
    }

    public static ExternalParticipantPolicy readExternalParticipant(String json) {
        ExternalParticipantDto dto = JdbcJson.read(json, ExternalParticipantDto.class);
        return dto == null ? null : new ExternalParticipantPolicy(
                dto.externalParticipantsAllowed(),
                dto.redactExternalContent(),
                dto.requireOrganizerApproval()
        );
    }

    public static String writeQuotas(QuotaLimits quotas) {
        return JdbcJson.write(new QuotaLimitsDto(
                quotas.dailyMeetingLimit(),
                quotas.dailyTranscriptMinutes(),
                quotas.dailyInputTokenLimit(),
                quotas.dailyOutputTokenLimit(),
                quotas.maxConcurrentAiJobs(),
                quotas.maxTranscriptDurationMinutes(),
                quotas.maxFileSizeBytes()
        ));
    }

    public static QuotaLimits readQuotas(String json) {
        QuotaLimitsDto dto = JdbcJson.read(json, QuotaLimitsDto.class);
        return dto == null ? null : new QuotaLimits(
                dto.dailyMeetingLimit(),
                dto.dailyTranscriptMinutes(),
                dto.dailyInputTokenLimit(),
                dto.dailyOutputTokenLimit(),
                dto.maxConcurrentAiJobs(),
                dto.maxTranscriptDurationMinutes(),
                dto.maxFileSizeBytes()
        );
    }

    public static String writeTenantPolicy(TenantPolicy policy) {
        return JdbcJson.write(new TenantPolicyDto(
                policy.tenantId().value(),
                new RetentionDto(policy.retention().retentionDays(), policy.retention().legalHoldAllowed()),
                new DeliveryDto(policy.delivery().externalDeliveryAllowed(), policy.delivery().requireApproval()),
                new ModelAccessDto(
                        new LinkedHashSet<>(policy.modelAccess().allowedModelKeys()),
                        policy.modelAccess().criticalMeetingFallbackAllowed()
                ),
                new ProcessingSlaDto(
                        policy.processingSla().defaultLevel().name(),
                        toStringKeyedLatency(policy.processingSla().targetLatencyMinutes())
                ),
                new ConcurrencyDto(
                        policy.concurrency().maxConcurrentAiJobs(),
                        policy.concurrency().maxConcurrentTranscriptJobs()
                ),
                new ExternalParticipantDto(
                        policy.externalParticipant().externalParticipantsAllowed(),
                        policy.externalParticipant().redactExternalContent(),
                        policy.externalParticipant().requireOrganizerApproval()
                ),
                new QuotaLimitsDto(
                        policy.quotas().dailyMeetingLimit(),
                        policy.quotas().dailyTranscriptMinutes(),
                        policy.quotas().dailyInputTokenLimit(),
                        policy.quotas().dailyOutputTokenLimit(),
                        policy.quotas().maxConcurrentAiJobs(),
                        policy.quotas().maxTranscriptDurationMinutes(),
                        policy.quotas().maxFileSizeBytes()
                ),
                policy.version()
        ));
    }

    public static TenantPolicy readTenantPolicy(String json) {
        TenantPolicyDto dto = JdbcJson.read(json, TenantPolicyDto.class);
        if (dto == null) {
            return null;
        }
        EnumMap<SlaLevel, Integer> latency = new EnumMap<>(SlaLevel.class);
        dto.processingSla().targetLatencyMinutes().forEach((k, v) -> latency.put(SlaLevel.valueOf(k), v));
        return new TenantPolicy(
                TenantId.of(dto.tenantId()),
                new RetentionPolicy(dto.retention().retentionDays(), dto.retention().legalHoldAllowed()),
                new DeliveryPolicy(dto.delivery().externalDeliveryAllowed(), dto.delivery().requireApproval()),
                new ModelAccessPolicy(
                        dto.modelAccess().allowedModelKeys(),
                        dto.modelAccess().criticalMeetingFallbackAllowed()
                ),
                new ProcessingSlaPolicy(SlaLevel.valueOf(dto.processingSla().defaultLevel()), latency),
                new ConcurrencyPolicy(dto.concurrency().maxConcurrentAiJobs(), dto.concurrency().maxConcurrentTranscriptJobs()),
                new ExternalParticipantPolicy(
                        dto.externalParticipant().externalParticipantsAllowed(),
                        dto.externalParticipant().redactExternalContent(),
                        dto.externalParticipant().requireOrganizerApproval()
                ),
                new QuotaLimits(
                        dto.quotas().dailyMeetingLimit(),
                        dto.quotas().dailyTranscriptMinutes(),
                        dto.quotas().dailyInputTokenLimit(),
                        dto.quotas().dailyOutputTokenLimit(),
                        dto.quotas().maxConcurrentAiJobs(),
                        dto.quotas().maxTranscriptDurationMinutes(),
                        dto.quotas().maxFileSizeBytes()
                ),
                dto.version()
        );
    }

    public static TenantPolicyOverride readOverride(
            TenantId tenantId,
            String retentionJson,
            String deliveryJson,
            String modelAccessJson,
            String processingSlaJson,
            String concurrencyJson,
            String externalParticipantJson,
            String quotasJson
    ) {
        TenantPolicyOverride.Builder builder = TenantPolicyOverride.builder(tenantId);
        Optional.ofNullable(readRetention(retentionJson)).ifPresent(builder::retention);
        Optional.ofNullable(readDelivery(deliveryJson)).ifPresent(builder::delivery);
        Optional.ofNullable(readModelAccess(modelAccessJson)).ifPresent(builder::modelAccess);
        Optional.ofNullable(readProcessingSla(processingSlaJson)).ifPresent(builder::processingSla);
        Optional.ofNullable(readConcurrency(concurrencyJson)).ifPresent(builder::concurrency);
        Optional.ofNullable(readExternalParticipant(externalParticipantJson)).ifPresent(builder::externalParticipant);
        Optional.ofNullable(readQuotas(quotasJson)).ifPresent(builder::quotas);
        return builder.build();
    }

    private record RetentionDto(int retentionDays, boolean legalHoldAllowed) {
    }

    private record DeliveryDto(boolean externalDeliveryAllowed, boolean requireApproval) {
    }

    private record ModelAccessDto(Set<String> allowedModelKeys, boolean criticalMeetingFallbackAllowed) {
    }

    private record ProcessingSlaDto(String defaultLevel, Map<String, Integer> targetLatencyMinutes) {
    }

    private static Map<String, Integer> toStringKeyedLatency(Map<SlaLevel, Integer> latency) {
        Map<String, Integer> mapped = new LinkedHashMap<>();
        latency.forEach((level, minutes) -> mapped.put(level.name(), minutes));
        return mapped;
    }

    private record ConcurrencyDto(int maxConcurrentAiJobs, int maxConcurrentTranscriptJobs) {
    }

    private record ExternalParticipantDto(
            boolean externalParticipantsAllowed,
            boolean redactExternalContent,
            boolean requireOrganizerApproval
    ) {
    }

    private record QuotaLimitsDto(
            int dailyMeetingLimit,
            int dailyTranscriptMinutes,
            long dailyInputTokenLimit,
            long dailyOutputTokenLimit,
            int maxConcurrentAiJobs,
            int maxTranscriptDurationMinutes,
            long maxFileSizeBytes
    ) {
    }

    private record TenantPolicyDto(
            java.util.UUID tenantId,
            RetentionDto retention,
            DeliveryDto delivery,
            ModelAccessDto modelAccess,
            ProcessingSlaDto processingSla,
            ConcurrencyDto concurrency,
            ExternalParticipantDto externalParticipant,
            QuotaLimitsDto quotas,
            long version
    ) {
    }
}
