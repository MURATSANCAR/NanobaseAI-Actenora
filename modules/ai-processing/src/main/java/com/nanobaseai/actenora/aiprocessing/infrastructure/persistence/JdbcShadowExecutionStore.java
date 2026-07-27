package com.nanobaseai.actenora.aiprocessing.infrastructure.persistence;

import com.nanobaseai.actenora.aiprocessing.application.port.ShadowExecutionStorePort;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ShadowExecution;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class JdbcShadowExecutionStore implements ShadowExecutionStorePort {

    private static final RowMapper<ShadowExecution> MAPPER = (rs, rowNum) -> new ShadowExecution(
            rs.getObject("id", UUID.class),
            rs.getObject("job_id", UUID.class),
            rs.getObject("routing_decision_id", UUID.class),
            rs.getObject("champion_deployment_id", UUID.class),
            rs.getObject("challenger_deployment_id", UUID.class),
            rs.getObject("champion_model_definition_id", UUID.class),
            rs.getObject("challenger_model_definition_id", UUID.class),
            ShadowExecution.ShadowStatus.valueOf(rs.getString("status")),
            Optional.ofNullable(rs.getString("challenger_result_ref")),
            Optional.ofNullable(rs.getString("comparison_summary_safe")),
            JdbcInstant.get(rs, "created_at"),
            Optional.ofNullable(JdbcInstant.get(rs, "completed_at"))
    );

    private final JdbcTemplate jdbc;

    public JdbcShadowExecutionStore(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public void save(ShadowExecution shadowExecution) {
        jdbc.update("""
                        INSERT INTO aiprocessing.shadow_executions (
                            id, job_id, routing_decision_id,
                            champion_deployment_id, challenger_deployment_id,
                            champion_model_definition_id, challenger_model_definition_id,
                            status, challenger_result_ref, comparison_summary_safe,
                            created_at, completed_at
                        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                        ON CONFLICT (id) DO UPDATE SET
                            status = EXCLUDED.status,
                            challenger_result_ref = EXCLUDED.challenger_result_ref,
                            comparison_summary_safe = EXCLUDED.comparison_summary_safe,
                            completed_at = EXCLUDED.completed_at
                        """,
                shadowExecution.shadowId(),
                shadowExecution.jobId(),
                shadowExecution.routingDecisionId(),
                shadowExecution.championDeploymentId(),
                shadowExecution.challengerDeploymentId(),
                shadowExecution.championModelDefinitionId(),
                shadowExecution.challengerModelDefinitionId(),
                shadowExecution.status().name(),
                shadowExecution.challengerResultRef().orElse(null),
                shadowExecution.comparisonSummarySafe().orElse(null),
                JdbcInstant.toTimestamp(shadowExecution.createdAt()),
                shadowExecution.completedAt().map(JdbcInstant::toTimestamp).orElse(null)
        );
    }

    @Override
    public Optional<ShadowExecution> findById(UUID shadowId) {
        List<ShadowExecution> rows = jdbc.query(
                "SELECT * FROM aiprocessing.shadow_executions WHERE id = ?",
                MAPPER,
                shadowId
        );
        return rows.stream().findFirst();
    }

    @Override
    public List<ShadowExecution> findByJobId(UUID jobId) {
        return jdbc.query(
                "SELECT * FROM aiprocessing.shadow_executions WHERE job_id = ? ORDER BY created_at",
                MAPPER,
                jobId
        );
    }
}
