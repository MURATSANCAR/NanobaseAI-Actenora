package com.nanobaseai.actenora.security.microsoftconnection;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class JdbcGraphWorkerLeaseStore implements GraphWorkerLeaseStore {

    private final JdbcTemplate jdbc;

    public JdbcGraphWorkerLeaseStore(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public boolean tryAcquire(String leaseName, String ownerId, Instant now, Duration duration) {
        int updated = jdbc.update(
                """
                INSERT INTO microsoftconnection.worker_lease (
                    lease_name, owner_id, locked_until, updated_at
                ) VALUES (?, ?, ?, ?)
                ON CONFLICT (lease_name) DO UPDATE SET
                    owner_id = EXCLUDED.owner_id,
                    locked_until = EXCLUDED.locked_until,
                    updated_at = EXCLUDED.updated_at
                WHERE microsoftconnection.worker_lease.locked_until < ?
                   OR microsoftconnection.worker_lease.owner_id = EXCLUDED.owner_id
                """,
                leaseName, ownerId, Timestamp.from(now.plus(duration)), Timestamp.from(now), Timestamp.from(now));
        return updated == 1;
    }

    @Override
    public void release(String leaseName, String ownerId, Instant now) {
        jdbc.update(
                """
                UPDATE microsoftconnection.worker_lease
                SET locked_until = ?, updated_at = ?
                WHERE lease_name = ? AND owner_id = ?
                """,
                Timestamp.from(now), Timestamp.from(now), leaseName, ownerId);
    }
}
