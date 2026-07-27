package com.nanobaseai.actenora.security.microsoftconnection;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class JdbcMailboxSyncWorkStore implements MailboxSyncWorkStore {

    private final JdbcTemplate jdbc;

    public JdbcMailboxSyncWorkStore(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public void enqueue(UUID tenantId, String mailboxUserId, Instant now) {
        jdbc.update(
                """
                INSERT INTO microsoftconnection.mailbox_sync_work (
                    tenant_id, mailbox_user_id, status, attempt_count,
                    next_attempt_at, created_at, updated_at
                ) VALUES (?, ?, 'PENDING', 0, ?, ?, ?)
                ON CONFLICT (tenant_id, mailbox_user_id) DO UPDATE SET
                    status = 'PENDING',
                    attempt_count = 0,
                    next_attempt_at = EXCLUDED.next_attempt_at,
                    claimed_at = NULL,
                    completed_at = NULL,
                    failure_code = NULL,
                    updated_at = EXCLUDED.updated_at
                WHERE microsoftconnection.mailbox_sync_work.status = 'COMPLETED'
                """,
                tenantId, mailboxUserId, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
    }

    @Override
    public List<WorkItem> claimDue(Instant now, int limit, Duration staleClaimAfter) {
        Instant staleBefore = now.minus(staleClaimAfter);
        return jdbc.query(
                """
                WITH due AS (
                    SELECT tenant_id, mailbox_user_id
                    FROM microsoftconnection.mailbox_sync_work
                    WHERE (
                        status IN ('PENDING', 'RETRY') AND next_attempt_at <= ?
                    ) OR (
                        status = 'PROCESSING' AND claimed_at < ?
                    )
                    ORDER BY next_attempt_at, created_at
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE microsoftconnection.mailbox_sync_work work
                SET status = 'PROCESSING', claimed_at = ?, updated_at = ?
                FROM due
                WHERE work.tenant_id = due.tenant_id
                  AND work.mailbox_user_id = due.mailbox_user_id
                RETURNING work.tenant_id, work.mailbox_user_id,
                          work.attempt_count, work.created_at
                """,
                (rs, rowNum) -> new WorkItem(
                        rs.getObject("tenant_id", UUID.class),
                        rs.getString("mailbox_user_id"),
                        rs.getInt("attempt_count"),
                        rs.getTimestamp("created_at").toInstant()),
                Timestamp.from(now), Timestamp.from(staleBefore), limit,
                Timestamp.from(now), Timestamp.from(now));
    }

    @Override
    public void complete(UUID tenantId, String mailboxUserId, Instant now) {
        jdbc.update(
                """
                UPDATE microsoftconnection.mailbox_sync_work
                SET status = 'COMPLETED', attempt_count = 0, failure_code = NULL,
                    claimed_at = NULL, completed_at = ?, updated_at = ?
                WHERE tenant_id = ? AND mailbox_user_id = ?
                  AND status <> 'COMPLETED'
                """,
                Timestamp.from(now), Timestamp.from(now), tenantId, mailboxUserId);
    }

    @Override
    public void reschedule(
            UUID tenantId,
            String mailboxUserId,
            int attemptCount,
            Instant nextAttemptAt,
            String failureCode,
            Instant now
    ) {
        int updated = jdbc.update(
                """
                UPDATE microsoftconnection.mailbox_sync_work
                SET status = 'RETRY', attempt_count = ?, next_attempt_at = ?,
                    failure_code = ?, claimed_at = NULL, updated_at = ?
                WHERE tenant_id = ? AND mailbox_user_id = ?
                """,
                attemptCount, Timestamp.from(nextAttemptAt), truncate(failureCode),
                Timestamp.from(now), tenantId, mailboxUserId);
        if (updated == 0) {
            jdbc.update(
                    """
                    INSERT INTO microsoftconnection.mailbox_sync_work (
                        tenant_id, mailbox_user_id, status, attempt_count,
                        next_attempt_at, failure_code, created_at, updated_at
                    ) VALUES (?, ?, 'RETRY', ?, ?, ?, ?, ?)
                    ON CONFLICT (tenant_id, mailbox_user_id) DO UPDATE SET
                        status = 'RETRY',
                        attempt_count = EXCLUDED.attempt_count,
                        next_attempt_at = EXCLUDED.next_attempt_at,
                        failure_code = EXCLUDED.failure_code,
                        claimed_at = NULL,
                        updated_at = EXCLUDED.updated_at
                    """,
                    tenantId, mailboxUserId, attemptCount, Timestamp.from(nextAttemptAt),
                    truncate(failureCode), Timestamp.from(now), Timestamp.from(now));
        }
    }

    @Override
    public long countPending() {
        Long count = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM microsoftconnection.mailbox_sync_work
                WHERE status IN ('PENDING', 'RETRY', 'PROCESSING')
                """,
                Long.class);
        return count == null ? 0L : count;
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= 512) {
            return value;
        }
        return value.substring(0, 512);
    }
}
