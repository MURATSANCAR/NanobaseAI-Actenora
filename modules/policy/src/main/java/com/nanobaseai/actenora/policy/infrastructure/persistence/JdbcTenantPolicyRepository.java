package com.nanobaseai.actenora.policy.infrastructure.persistence;

import com.nanobaseai.actenora.policy.application.TenantPolicyRepositoryPort;
import com.nanobaseai.actenora.policy.domain.QuotaLimits;
import com.nanobaseai.actenora.policy.domain.TenantPolicy;
import com.nanobaseai.actenora.policy.domain.TenantPolicyOverride;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Optional;

/** JDBC tenant policy store ({@code policy.tenant_policy_overrides}, {@code policy.tenant_policy_materialized}). */
public final class JdbcTenantPolicyRepository implements TenantPolicyRepositoryPort {

    private static final RowMapper<TenantPolicyOverride> OVERRIDE_MAPPER = (rs, rowNum) ->
            PolicyJsonCodec.readOverride(
                    TenantId.of(rs.getObject("tenant_id", java.util.UUID.class)),
                    rs.getString("retention_json"),
                    rs.getString("delivery_json"),
                    rs.getString("model_access_json"),
                    rs.getString("processing_sla_json"),
                    rs.getString("concurrency_json"),
                    rs.getString("external_participant_json"),
                    rs.getString("quotas_json")
            );

    private static final RowMapper<TenantPolicy> MATERIALIZED_MAPPER = (rs, rowNum) ->
            PolicyJsonCodec.readTenantPolicy(rs.getString("policy_json"));

    private final JdbcTemplate jdbc;

    public JdbcTenantPolicyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public Optional<TenantPolicyOverride> findOverride(TenantId tenantId) {
        String sql = """
                SELECT tenant_id, retention_json, delivery_json, model_access_json,
                       processing_sla_json, concurrency_json, external_participant_json, quotas_json
                FROM policy.tenant_policy_overrides WHERE tenant_id = ?
                """;
        return jdbc.query(sql, OVERRIDE_MAPPER, tenantId.value()).stream().findFirst();
    }

    @Override
    public void saveOverride(TenantPolicyOverride override) {
        QuotaLimits quotas = override.quotas().orElse(null);
        String sql = """
                INSERT INTO policy.tenant_policy_overrides (
                    tenant_id, retention_json, delivery_json, model_access_json, processing_sla_json,
                    concurrency_json, external_participant_json, quotas_json,
                    daily_meeting_limit, daily_transcript_minutes, daily_input_token_limit,
                    daily_output_token_limit, max_concurrent_ai_jobs, max_transcript_duration_minutes,
                    max_file_size_bytes, critical_meeting_fallback_allowed, updated_at, version
                ) VALUES (?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb,
                          ?, ?, ?, ?, ?, ?, ?, ?, NOW(), 0)
                ON CONFLICT (tenant_id) DO UPDATE SET
                    retention_json = EXCLUDED.retention_json,
                    delivery_json = EXCLUDED.delivery_json,
                    model_access_json = EXCLUDED.model_access_json,
                    processing_sla_json = EXCLUDED.processing_sla_json,
                    concurrency_json = EXCLUDED.concurrency_json,
                    external_participant_json = EXCLUDED.external_participant_json,
                    quotas_json = EXCLUDED.quotas_json,
                    daily_meeting_limit = EXCLUDED.daily_meeting_limit,
                    daily_transcript_minutes = EXCLUDED.daily_transcript_minutes,
                    daily_input_token_limit = EXCLUDED.daily_input_token_limit,
                    daily_output_token_limit = EXCLUDED.daily_output_token_limit,
                    max_concurrent_ai_jobs = EXCLUDED.max_concurrent_ai_jobs,
                    max_transcript_duration_minutes = EXCLUDED.max_transcript_duration_minutes,
                    max_file_size_bytes = EXCLUDED.max_file_size_bytes,
                    critical_meeting_fallback_allowed = EXCLUDED.critical_meeting_fallback_allowed,
                    updated_at = NOW(),
                    version = policy.tenant_policy_overrides.version + 1
                """;
        jdbc.update(sql,
                override.tenantId().value(),
                jsonOrNull(override.retention().map(PolicyJsonCodec::writeRetention).orElse(null)),
                jsonOrNull(override.delivery().map(PolicyJsonCodec::writeDelivery).orElse(null)),
                jsonOrNull(override.modelAccess().map(PolicyJsonCodec::writeModelAccess).orElse(null)),
                jsonOrNull(override.processingSla().map(PolicyJsonCodec::writeProcessingSla).orElse(null)),
                jsonOrNull(override.concurrency().map(PolicyJsonCodec::writeConcurrency).orElse(null)),
                jsonOrNull(override.externalParticipant().map(PolicyJsonCodec::writeExternalParticipant).orElse(null)),
                jsonOrNull(override.quotas().map(PolicyJsonCodec::writeQuotas).orElse(null)),
                quotas == null ? null : quotas.dailyMeetingLimit(),
                quotas == null ? null : quotas.dailyTranscriptMinutes(),
                quotas == null ? null : quotas.dailyInputTokenLimit(),
                quotas == null ? null : quotas.dailyOutputTokenLimit(),
                quotas == null ? null : quotas.maxConcurrentAiJobs(),
                quotas == null ? null : quotas.maxTranscriptDurationMinutes(),
                quotas == null ? null : quotas.maxFileSizeBytes(),
                override.modelAccess().map(m -> m.criticalMeetingFallbackAllowed()).orElse(null)
        );
    }

    @Override
    public Optional<TenantPolicy> findMaterialized(TenantId tenantId) {
        String sql = "SELECT policy_json FROM policy.tenant_policy_materialized WHERE tenant_id = ?";
        return jdbc.query(sql, MATERIALIZED_MAPPER, tenantId.value()).stream().findFirst();
    }

    @Override
    public void saveMaterialized(TenantPolicy policy) {
        String sql = """
                INSERT INTO policy.tenant_policy_materialized (tenant_id, policy_json, resolved_at, version)
                VALUES (?, ?::jsonb, NOW(), ?)
                ON CONFLICT (tenant_id) DO UPDATE SET
                    policy_json = EXCLUDED.policy_json,
                    resolved_at = NOW(),
                    version = EXCLUDED.version
                """;
        jdbc.update(sql,
                policy.tenantId().value(),
                PolicyJsonCodec.writeTenantPolicy(policy),
                policy.version()
        );
    }

    private static String jsonOrNull(String json) {
        return json == null || json.isBlank() ? null : json;
    }
}
