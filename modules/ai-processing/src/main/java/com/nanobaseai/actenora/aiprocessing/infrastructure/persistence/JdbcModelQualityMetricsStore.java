package com.nanobaseai.actenora.aiprocessing.infrastructure.persistence;

import com.nanobaseai.actenora.aiprocessing.application.port.ModelQualityMetricsPort;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ModelQualitySnapshot;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ModelRole;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class JdbcModelQualityMetricsStore implements ModelQualityMetricsPort {

    private static final RowMapper<ModelQualitySnapshot> MAPPER = (rs, rowNum) -> {
        long success = rs.getLong("success_count");
        long failure = rs.getLong("failure_count");
        long samples = rs.getLong("latency_samples");
        double latencySum = rs.getDouble("latency_sum_ms");
        long schemaPass = rs.getLong("schema_pass_count");
        long schemaTotal = rs.getLong("schema_total_count");
        double avgLatency = samples == 0 ? 0.0 : latencySum / samples;
        double schemaRate = schemaTotal == 0 ? 0.0 : (double) schemaPass / schemaTotal;
        return new ModelQualitySnapshot(
                rs.getObject("model_definition_id", UUID.class),
                rs.getString("model_key"),
                ModelRole.valueOf(rs.getString("role")),
                success,
                failure,
                avgLatency,
                schemaRate
        );
    };

    private final JdbcTemplate jdbc;

    public JdbcModelQualityMetricsStore(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public void recordSuccess(UUID modelDefinitionId, String modelKey, ModelRole role, long latencyMs, boolean schemaPassed) {
        upsert(modelDefinitionId, modelKey, role, 1, 0, latencyMs, schemaPassed ? 1 : 0, 1);
    }

    @Override
    public void recordFailure(UUID modelDefinitionId, String modelKey, ModelRole role, long latencyMs) {
        upsert(modelDefinitionId, modelKey, role, 0, 1, latencyMs, 0, 0);
    }

    private void upsert(
            UUID modelDefinitionId,
            String modelKey,
            ModelRole role,
            long successDelta,
            long failureDelta,
            long latencyMs,
            long schemaPassDelta,
            long schemaTotalDelta
    ) {
        jdbc.update("""
                        INSERT INTO aiprocessing.model_quality_metrics (
                            model_definition_id, model_key, role,
                            success_count, failure_count, latency_sum_ms, latency_samples,
                            schema_pass_count, schema_total_count, updated_at
                        ) VALUES (?,?,?,?,?,?,?,?,?,?)
                        ON CONFLICT (model_definition_id) DO UPDATE SET
                            model_key = EXCLUDED.model_key,
                            role = EXCLUDED.role,
                            success_count = aiprocessing.model_quality_metrics.success_count + EXCLUDED.success_count,
                            failure_count = aiprocessing.model_quality_metrics.failure_count + EXCLUDED.failure_count,
                            latency_sum_ms = aiprocessing.model_quality_metrics.latency_sum_ms + EXCLUDED.latency_sum_ms,
                            latency_samples = aiprocessing.model_quality_metrics.latency_samples + EXCLUDED.latency_samples,
                            schema_pass_count = aiprocessing.model_quality_metrics.schema_pass_count + EXCLUDED.schema_pass_count,
                            schema_total_count = aiprocessing.model_quality_metrics.schema_total_count + EXCLUDED.schema_total_count,
                            updated_at = EXCLUDED.updated_at
                        """,
                modelDefinitionId,
                modelKey,
                role.name(),
                successDelta,
                failureDelta,
                Math.max(0L, latencyMs),
                1L,
                schemaPassDelta,
                schemaTotalDelta,
                JdbcInstant.toTimestamp(Instant.now())
        );
    }

    @Override
    public Optional<ModelQualitySnapshot> snapshot(UUID modelDefinitionId) {
        List<ModelQualitySnapshot> rows = jdbc.query(
                "SELECT * FROM aiprocessing.model_quality_metrics WHERE model_definition_id = ?",
                MAPPER,
                modelDefinitionId
        );
        return rows.stream().findFirst();
    }

    @Override
    public List<ModelQualitySnapshot> allSnapshots() {
        return jdbc.query("SELECT * FROM aiprocessing.model_quality_metrics", MAPPER);
    }
}
