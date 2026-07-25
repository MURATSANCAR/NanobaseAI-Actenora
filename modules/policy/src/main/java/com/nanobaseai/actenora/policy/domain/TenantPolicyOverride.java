package com.nanobaseai.actenora.policy.domain;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Objects;
import java.util.Optional;

/** Partial tenant override layered on system defaults. */
public final class TenantPolicyOverride {

    private final TenantId tenantId;
    private final Optional<RetentionPolicy> retention;
    private final Optional<DeliveryPolicy> delivery;
    private final Optional<ModelAccessPolicy> modelAccess;
    private final Optional<ProcessingSlaPolicy> processingSla;
    private final Optional<ConcurrencyPolicy> concurrency;
    private final Optional<ExternalParticipantPolicy> externalParticipant;
    private final Optional<QuotaLimits> quotas;

    private TenantPolicyOverride(Builder builder) {
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId");
        this.retention = Optional.ofNullable(builder.retention);
        this.delivery = Optional.ofNullable(builder.delivery);
        this.modelAccess = Optional.ofNullable(builder.modelAccess);
        this.processingSla = Optional.ofNullable(builder.processingSla);
        this.concurrency = Optional.ofNullable(builder.concurrency);
        this.externalParticipant = Optional.ofNullable(builder.externalParticipant);
        this.quotas = Optional.ofNullable(builder.quotas);
    }

    public static Builder builder(TenantId tenantId) {
        return new Builder(tenantId);
    }

    public TenantId tenantId() { return tenantId; }
    public Optional<RetentionPolicy> retention() { return retention; }
    public Optional<DeliveryPolicy> delivery() { return delivery; }
    public Optional<ModelAccessPolicy> modelAccess() { return modelAccess; }
    public Optional<ProcessingSlaPolicy> processingSla() { return processingSla; }
    public Optional<ConcurrencyPolicy> concurrency() { return concurrency; }
    public Optional<ExternalParticipantPolicy> externalParticipant() { return externalParticipant; }
    public Optional<QuotaLimits> quotas() { return quotas; }

    public static final class Builder {
        private final TenantId tenantId;
        private RetentionPolicy retention;
        private DeliveryPolicy delivery;
        private ModelAccessPolicy modelAccess;
        private ProcessingSlaPolicy processingSla;
        private ConcurrencyPolicy concurrency;
        private ExternalParticipantPolicy externalParticipant;
        private QuotaLimits quotas;

        private Builder(TenantId tenantId) { this.tenantId = tenantId; }
        public Builder retention(RetentionPolicy retention) { this.retention = retention; return this; }
        public Builder delivery(DeliveryPolicy delivery) { this.delivery = delivery; return this; }
        public Builder modelAccess(ModelAccessPolicy modelAccess) { this.modelAccess = modelAccess; return this; }
        public Builder processingSla(ProcessingSlaPolicy processingSla) { this.processingSla = processingSla; return this; }
        public Builder concurrency(ConcurrencyPolicy concurrency) { this.concurrency = concurrency; return this; }
        public Builder externalParticipant(ExternalParticipantPolicy externalParticipant) { this.externalParticipant = externalParticipant; return this; }
        public Builder quotas(QuotaLimits quotas) { this.quotas = quotas; return this; }
        public TenantPolicyOverride build() { return new TenantPolicyOverride(this); }
    }
}
