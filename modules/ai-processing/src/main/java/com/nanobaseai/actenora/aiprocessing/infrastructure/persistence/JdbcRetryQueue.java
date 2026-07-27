package com.nanobaseai.actenora.aiprocessing.infrastructure.persistence;

import com.nanobaseai.actenora.aiprocessing.application.port.RetryQueuePort;
import com.nanobaseai.actenora.aiprocessing.domain.routing.RoutingDecision;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class JdbcRetryQueue implements RetryQueuePort {

    private final JdbcTemplate jdbc;

    public JdbcRetryQueue(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public void enqueue(RoutingDecision decision) {
        jdbc.update("""
                        INSERT INTO aiprocessing.retry_queue (job_id, routing_decision_id, enqueued_at)
                        VALUES (?,?,?)
                        ON CONFLICT (job_id) DO UPDATE SET
                            routing_decision_id = EXCLUDED.routing_decision_id,
                            enqueued_at = EXCLUDED.enqueued_at
                        """,
                decision.jobId(),
                decision.decisionId(),
                JdbcInstant.toTimestamp(Instant.now())
        );
    }

    @Override
    public List<UUID> pendingJobIds() {
        return jdbc.query(
                "SELECT job_id FROM aiprocessing.retry_queue ORDER BY enqueued_at",
                (rs, rowNum) -> rs.getObject("job_id", UUID.class)
        );
    }

    @Override
    public boolean remove(UUID jobId) {
        return jdbc.update("DELETE FROM aiprocessing.retry_queue WHERE job_id = ?", jobId) > 0;
    }
}
