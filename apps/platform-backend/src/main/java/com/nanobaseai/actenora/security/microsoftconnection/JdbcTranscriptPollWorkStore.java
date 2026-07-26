package com.nanobaseai.actenora.security.microsoftconnection;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class JdbcTranscriptPollWorkStore implements TranscriptPollWorkStore {

    private final JdbcTemplate jdbc;

    public JdbcTranscriptPollWorkStore(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public void enqueue(UUID tenantId, UUID meetingOccurrenceId, Instant now) {
        jdbc.update(
                """
                INSERT INTO microsoftconnection.transcript_poll_work (
                    tenant_id, meeting_occurrence_id, status, attempt_count,
                    next_attempt_at, created_at, updated_at
                ) VALUES (?, ?, 'PENDING', 0, ?, ?, ?)
                ON CONFLICT (tenant_id, meeting_occurrence_id) DO NOTHING
                """,
                tenantId, meetingOccurrenceId, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
    }

    @Override
    public List<WorkItem> claimDue(Instant now, int limit, Duration staleClaimAfter) {
        Instant staleBefore = now.minus(staleClaimAfter);
        return jdbc.query(
                """
                WITH due AS (
                    SELECT tenant_id, meeting_occurrence_id
                    FROM microsoftconnection.transcript_poll_work
                    WHERE (
                        status IN ('PENDING', 'RETRY') AND next_attempt_at <= ?
                    ) OR (
                        status = 'PROCESSING' AND claimed_at < ?
                    )
                    ORDER BY next_attempt_at, created_at
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE microsoftconnection.transcript_poll_work work
                SET status = 'PROCESSING', claimed_at = ?, updated_at = ?
                FROM due
                WHERE work.tenant_id = due.tenant_id
                  AND work.meeting_occurrence_id = due.meeting_occurrence_id
                RETURNING work.tenant_id, work.meeting_occurrence_id,
                          work.attempt_count, work.created_at
                """,
                (rs, rowNum) -> new WorkItem(
                        rs.getObject("tenant_id", UUID.class),
                        rs.getObject("meeting_occurrence_id", UUID.class),
                        rs.getInt("attempt_count"),
                        rs.getTimestamp("created_at").toInstant()),
                Timestamp.from(now), Timestamp.from(staleBefore), limit,
                Timestamp.from(now), Timestamp.from(now));
    }

    @Override
    public void complete(UUID tenantId, UUID meetingOccurrenceId, Instant now) {
        updateTerminal("COMPLETED", tenantId, meetingOccurrenceId, 0, null, now);
    }

    @Override
    public void reschedule(
            UUID tenantId,
            UUID meetingOccurrenceId,
            int attemptCount,
            Instant nextAttemptAt,
            String failureCode,
            Instant now
    ) {
        jdbc.update(
                """
                UPDATE microsoftconnection.transcript_poll_work
                SET status = 'RETRY', attempt_count = ?, next_attempt_at = ?,
                    failure_code = ?, claimed_at = NULL, updated_at = ?
                WHERE tenant_id = ? AND meeting_occurrence_id = ?
                """,
                attemptCount, Timestamp.from(nextAttemptAt), truncate(failureCode),
                Timestamp.from(now), tenantId, meetingOccurrenceId);
    }

    @Override
    public void deadLetter(
            UUID tenantId,
            UUID meetingOccurrenceId,
            int attemptCount,
            String failureCode,
            Instant now
    ) {
        updateTerminal("DEAD_LETTER", tenantId, meetingOccurrenceId, attemptCount, failureCode, now);
    }

    @Override
    public boolean requeueDeadLetter(UUID tenantId, UUID meetingOccurrenceId, Instant now) {
        return jdbc.update(
                """
                UPDATE microsoftconnection.transcript_poll_work
                SET status = 'PENDING', attempt_count = 0, next_attempt_at = ?,
                    claimed_at = NULL, completed_at = NULL, failure_code = NULL, updated_at = ?
                WHERE tenant_id = ? AND meeting_occurrence_id = ? AND status = 'DEAD_LETTER'
                """,
                Timestamp.from(now), Timestamp.from(now), tenantId, meetingOccurrenceId) == 1;
    }

    @Override
    public long countPending() {
        Long count = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM microsoftconnection.transcript_poll_work
                WHERE status IN ('PENDING', 'RETRY', 'PROCESSING')
                """,
                Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public Optional<Instant> oldestPendingCreatedAt() {
        Timestamp timestamp = jdbc.queryForObject(
                """
                SELECT MIN(created_at) FROM microsoftconnection.transcript_poll_work
                WHERE status IN ('PENDING', 'RETRY', 'PROCESSING')
                """,
                Timestamp.class);
        return Optional.ofNullable(timestamp).map(Timestamp::toInstant);
    }

    private void updateTerminal(
            String status,
            UUID tenantId,
            UUID meetingOccurrenceId,
            int attemptCount,
            String failureCode,
            Instant now
    ) {
        jdbc.update(
                """
                UPDATE microsoftconnection.transcript_poll_work
                SET status = ?, attempt_count = ?, failure_code = ?,
                    claimed_at = NULL, completed_at = ?, updated_at = ?
                WHERE tenant_id = ? AND meeting_occurrence_id = ?
                """,
                status, attemptCount, truncate(failureCode), Timestamp.from(now),
                Timestamp.from(now), tenantId, meetingOccurrenceId);
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= 512) {
            return value;
        }
        return value.substring(0, 512);
    }
}
