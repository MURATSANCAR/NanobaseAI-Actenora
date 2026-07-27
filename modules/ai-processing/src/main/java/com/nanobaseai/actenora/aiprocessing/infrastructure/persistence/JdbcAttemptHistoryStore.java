package com.nanobaseai.actenora.aiprocessing.infrastructure.persistence;

import com.nanobaseai.actenora.aiprocessing.application.port.AttemptHistoryPort;
import com.nanobaseai.actenora.aiprocessing.domain.routing.AttemptHistory;
import com.nanobaseai.actenora.aiprocessing.domain.routing.AttemptRecord;
import com.nanobaseai.actenora.aiprocessing.domain.routing.FallbackStep;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ModelRole;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class JdbcAttemptHistoryStore implements AttemptHistoryPort {

    private static final RowMapper<AttemptRecord> MAPPER = (rs, rowNum) -> new AttemptRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("job_id", UUID.class),
            rs.getObject("routing_decision_id", UUID.class),
            rs.getInt("attempt_number"),
            rs.getObject("model_definition_id", UUID.class),
            rs.getObject("deployment_id", UUID.class),
            rs.getString("model_key"),
            ModelRole.valueOf(rs.getString("role")),
            FallbackStep.valueOf(rs.getString("fallback_step")),
            AttemptRecord.AttemptStatus.valueOf(rs.getString("status")),
            rs.getBoolean("quality_downgraded"),
            Optional.ofNullable(rs.getString("failure_category")),
            Optional.ofNullable(rs.getString("failure_detail_safe")),
            JdbcInstant.get(rs, "started_at"),
            Optional.ofNullable(JdbcInstant.get(rs, "completed_at"))
    );

    private final JdbcTemplate jdbc;

    public JdbcAttemptHistoryStore(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public AttemptHistory getOrCreate(UUID jobId) {
        return find(jobId).orElseGet(() -> new AttemptHistory(jobId));
    }

    @Override
    public void append(AttemptRecord attempt) {
        jdbc.update("""
                        INSERT INTO aiprocessing.attempt_history (
                            id, job_id, routing_decision_id, attempt_number,
                            model_definition_id, deployment_id, model_key, role, fallback_step,
                            status, quality_downgraded, failure_category, failure_detail_safe,
                            started_at, completed_at
                        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """,
                attempt.attemptId(),
                attempt.jobId(),
                attempt.routingDecisionId(),
                attempt.attemptNumber(),
                attempt.modelDefinitionId(),
                attempt.deploymentId(),
                attempt.modelKey(),
                attempt.role().name(),
                attempt.fallbackStep().name(),
                attempt.status().name(),
                attempt.qualityDowngraded(),
                attempt.failureCategory().orElse(null),
                attempt.failureDetailSafe().orElse(null),
                JdbcInstant.toTimestamp(attempt.startedAt()),
                attempt.completedAt().map(JdbcInstant::toTimestamp).orElse(null)
        );
    }

    @Override
    public void complete(AttemptRecord completed) {
        int updated = jdbc.update("""
                        UPDATE aiprocessing.attempt_history
                        SET status = ?, quality_downgraded = ?, failure_category = ?,
                            failure_detail_safe = ?, completed_at = ?
                        WHERE id = ?
                        """,
                completed.status().name(),
                completed.qualityDowngraded(),
                completed.failureCategory().orElse(null),
                completed.failureDetailSafe().orElse(null),
                completed.completedAt().map(JdbcInstant::toTimestamp).orElse(null),
                completed.attemptId()
        );
        if (updated == 0) {
            throw new IllegalArgumentException("unknown attempt " + completed.attemptId());
        }
    }

    @Override
    public Optional<AttemptHistory> find(UUID jobId) {
        List<AttemptRecord> rows = jdbc.query(
                "SELECT * FROM aiprocessing.attempt_history WHERE job_id = ? ORDER BY attempt_number",
                MAPPER,
                jobId
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        AttemptHistory history = new AttemptHistory(jobId);
        for (AttemptRecord row : rows) {
            history.append(row);
        }
        return Optional.of(history);
    }
}
