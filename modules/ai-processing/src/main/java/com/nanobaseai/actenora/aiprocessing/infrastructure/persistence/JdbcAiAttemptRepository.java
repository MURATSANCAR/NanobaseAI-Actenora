package com.nanobaseai.actenora.aiprocessing.infrastructure.persistence;

import com.nanobaseai.actenora.aiprocessing.application.port.AiAttemptRepository;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiAttempt;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiAttemptStatus;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC AI attempt store ({@code aiprocessing.ai_attempts}). */
public final class JdbcAiAttemptRepository implements AiAttemptRepository {

    private static final String COLUMNS = """
            id, ai_job_id, attempt_number, model_definition_id, model_deployment_id, status,
            latency_ms, input_tokens, output_tokens, retryable, failure_category, failure_detail_safe,
            started_at, completed_at
            """;

    private static final RowMapper<AiAttempt> ROW_MAPPER = (rs, rowNum) -> new AiAttempt(
            rs.getObject("id", UUID.class),
            rs.getObject("ai_job_id", UUID.class),
            rs.getInt("attempt_number"),
            rs.getObject("model_definition_id", UUID.class),
            rs.getObject("model_deployment_id", UUID.class),
            AiAttemptStatus.valueOf(rs.getString("status")),
            (Long) rs.getObject("latency_ms"),
            (Integer) rs.getObject("input_tokens"),
            (Integer) rs.getObject("output_tokens"),
            rs.getBoolean("retryable"),
            rs.getString("failure_category"),
            rs.getString("failure_detail_safe"),
            JdbcInstant.get(rs, "started_at"),
            JdbcInstant.get(rs, "completed_at")
    );

    private final JdbcTemplate jdbc;

    public JdbcAiAttemptRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public void save(AiAttempt attempt) {
        if (exists(attempt.id())) {
            update(attempt);
            return;
        }
        insert(attempt);
    }

    @Override
    public Optional<AiAttempt> findById(UUID id) {
        String sql = "SELECT " + COLUMNS + " FROM aiprocessing.ai_attempts WHERE id = ?";
        return jdbc.query(sql, ROW_MAPPER, id).stream().findFirst();
    }

    @Override
    public List<AiAttempt> findByJobId(UUID jobId) {
        String sql = "SELECT " + COLUMNS + " FROM aiprocessing.ai_attempts WHERE ai_job_id = ? ORDER BY attempt_number";
        return jdbc.query(sql, ROW_MAPPER, jobId);
    }

    @Override
    public Optional<AiAttempt> findActiveByJobId(UUID jobId) {
        String sql = "SELECT " + COLUMNS + """
                 FROM aiprocessing.ai_attempts
                 WHERE ai_job_id = ? AND status = 'STARTED'
                 ORDER BY attempt_number DESC LIMIT 1
                """;
        return jdbc.query(sql, ROW_MAPPER, jobId).stream().findFirst();
    }

    private boolean exists(UUID id) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM aiprocessing.ai_attempts WHERE id = ?",
                Integer.class,
                id
        );
        return count != null && count > 0;
    }

    private void insert(AiAttempt attempt) {
        String sql = """
                INSERT INTO aiprocessing.ai_attempts (
                    id, ai_job_id, attempt_number, model_definition_id, model_deployment_id, status,
                    latency_ms, input_tokens, output_tokens, retryable, failure_category, failure_detail_safe,
                    started_at, completed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbc.update(sql,
                attempt.id(),
                attempt.aiJobId(),
                attempt.attemptNumber(),
                attempt.modelDefinitionId(),
                attempt.modelDeploymentId(),
                attempt.status().name(),
                attempt.latencyMs().orElse(null),
                attempt.inputTokens().orElse(null),
                attempt.outputTokens().orElse(null),
                attempt.retryable(),
                attempt.failureCategory().orElse(null),
                attempt.failureDetailSafe().orElse(null),
                JdbcInstant.toTimestamp(attempt.startedAt()),
                attempt.completedAt().map(JdbcInstant::toTimestamp).orElse(null)
        );
    }

    private void update(AiAttempt attempt) {
        String sql = """
                UPDATE aiprocessing.ai_attempts SET
                    status = ?, latency_ms = ?, input_tokens = ?, output_tokens = ?,
                    retryable = ?, failure_category = ?, failure_detail_safe = ?, completed_at = ?
                WHERE id = ?
                """;
        jdbc.update(sql,
                attempt.status().name(),
                attempt.latencyMs().orElse(null),
                attempt.inputTokens().orElse(null),
                attempt.outputTokens().orElse(null),
                attempt.retryable(),
                attempt.failureCategory().orElse(null),
                attempt.failureDetailSafe().orElse(null),
                attempt.completedAt().map(JdbcInstant::toTimestamp).orElse(null),
                attempt.id()
        );
    }
}
