package ai.nanobase.actenora.policy.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Effective policy snapshot for a tenant (defaults merged with overrides).
 */
public final class TenantPolicy {

    private final UUID tenantId;
    private final RetentionPolicy retention;
    private final DeliveryPolicy delivery;
    private final ModelAccessPolicy modelAccess;
    private final ProcessingSlaPolicy processingSla;
    private final ConcurrencyPolicy concurrency;
    private final ExternalParticipantPolicy externalParticipant;
    private final QuotaLimits quotas;
    private final long version;

    public TenantPolicy(
            UUID tenantId,
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

    public static TenantPolicy systemDefaultsFor(UUID tenantId) {
        ConcurrencyPolicy concurrency = ConcurrencyPolicy.systemDefaults();
        QuotaLimits quotas = QuotaLimits.systemDefaults();
        // Keep concurrency and quota AI job ceilings aligned by default.
        quotas = new QuotaLimits(
                quotas.dailyMeetingLimit(),
                quotas.dailyTranscriptMinutes(),
                quotas.dailyInputTokenLimit(),
                quotas.dailyOutputTokenLimit(),
                concurrency.maxConcurrentAiJobs(),
                quotas.maxTranscriptDurationMinutes(),
                quotas.maxFileSizeBytes()
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

    public UUID tenantId() {
        return tenantId;
    }

    public RetentionPolicy retention() {
        return retention;
    }

    public DeliveryPolicy delivery() {
        return delivery;
    }

    public ModelAccessPolicy modelAccess() {
        return modelAccess;
    }

    public ProcessingSlaPolicy processingSla() {
        return processingSla;
    }

    public ConcurrencyPolicy concurrency() {
        return concurrency;
    }

    public ExternalParticipantPolicy externalParticipant() {
        return externalParticipant;
    }

    public QuotaLimits quotas() {
        return quotas;
    }

    public long version() {
        return version;
    }
}
