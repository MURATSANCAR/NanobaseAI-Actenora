package com.nanobaseai.actenora.policy.domain;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Objects;

/** Effective policy snapshot for a tenant (defaults merged with overrides). */
public final class TenantPolicy {

    private final TenantId tenantId;
    private final RetentionPolicy retention;
    private final DeliveryPolicy delivery;
    private final ModelAccessPolicy modelAccess;
    private final ProcessingSlaPolicy processingSla;
    private final ConcurrencyPolicy concurrency;
    private final ExternalParticipantPolicy externalParticipant;
    private final QuotaLimits quotas;
    private final long version;

    public TenantPolicy(
            TenantId tenantId,
            RetentionPolicy retention,
            DeliveryPolicy delivery,
            ModelAccessPolicy modelAccess,
            ProcessingSlaPolicy processingSla,
            ConcurrencyPolicy concurrency,
            ExternalParticipantPolicy externalParticipant,
            QuotaLimits quotas,
            long version
    ) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.retention = Objects.requireNonNull(retention, "retention");
        this.delivery = Objects.requireNonNull(delivery, "delivery");
        this.modelAccess = Objects.requireNonNull(modelAccess, "modelAccess");
        this.processingSla = Objects.requireNonNull(processingSla, "processingSla");
        this.concurrency = Objects.requireNonNull(concurrency, "concurrency");
        this.externalParticipant = Objects.requireNonNull(externalParticipant, "externalParticipant");
        this.quotas = Objects.requireNonNull(quotas, "quotas");
        this.version = version;
    }

    public static TenantPolicy systemDefaultsFor(TenantId tenantId) {
        ConcurrencyPolicy concurrency = ConcurrencyPolicy.systemDefaults();
        QuotaLimits base = QuotaLimits.systemDefaults();
        QuotaLimits quotas = new QuotaLimits(
                base.dailyMeetingLimit(),
                base.dailyTranscriptMinutes(),
                base.dailyInputTokenLimit(),
                base.dailyOutputTokenLimit(),
                concurrency.maxConcurrentAiJobs(),
                base.maxTranscriptDurationMinutes(),
                base.maxFileSizeBytes()
        );
        return new TenantPolicy(
                tenantId,
                RetentionPolicy.systemDefaults(),
                DeliveryPolicy.systemDefaults(),
                ModelAccessPolicy.systemDefaults(),
                ProcessingSlaPolicy.systemDefaults(),
                concurrency,
                ExternalParticipantPolicy.systemDefaults(),
                quotas,
                0L
        );
    }

    public TenantPolicy apply(TenantPolicyOverride override) {
        Objects.requireNonNull(override, "override");
        if (!tenantId.equals(override.tenantId())) {
            throw new IllegalArgumentException("override tenant mismatch");
        }
        return new TenantPolicy(
                tenantId,
                override.retention().orElse(retention),
                override.delivery().orElse(delivery),
                override.modelAccess().orElse(modelAccess),
                override.processingSla().orElse(processingSla),
                override.concurrency().orElse(concurrency),
                override.externalParticipant().orElse(externalParticipant),
                override.quotas().orElse(quotas),
                version + 1
        );
    }

    public TenantId tenantId() { return tenantId; }
    public RetentionPolicy retention() { return retention; }
    public DeliveryPolicy delivery() { return delivery; }
    public ModelAccessPolicy modelAccess() { return modelAccess; }
    public ProcessingSlaPolicy processingSla() { return processingSla; }
    public ConcurrencyPolicy concurrency() { return concurrency; }
    public ExternalParticipantPolicy externalParticipant() { return externalParticipant; }
    public QuotaLimits quotas() { return quotas; }
    public long version() { return version; }
}
