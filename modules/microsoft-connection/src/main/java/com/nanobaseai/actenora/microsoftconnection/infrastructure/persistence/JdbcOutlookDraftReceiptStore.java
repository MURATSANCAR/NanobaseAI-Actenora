package com.nanobaseai.actenora.microsoftconnection.infrastructure.persistence;

import com.nanobaseai.actenora.microsoftconnection.application.model.OutlookDraftResult;
import com.nanobaseai.actenora.microsoftconnection.application.port.OutlookDraftReceiptStore;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class JdbcOutlookDraftReceiptStore implements OutlookDraftReceiptStore {

    private static final RowMapper<Receipt> ROW_MAPPER = (rs, rowNum) -> new Receipt(
            rs.getObject("tenant_id", UUID.class),
            rs.getString("idempotency_key"),
            Status.valueOf(rs.getString("status")),
            rs.getString("provider_message_id"),
            rs.getString("web_link"),
            JdbcInstant.get(rs, "claimed_at"),
            JdbcInstant.get(rs, "completed_at"));

    private final JdbcTemplate jdbc;

    public JdbcOutlookDraftReceiptStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Receipt> find(UUID tenantId, String idempotencyKey) {
        return jdbc.query(
                """
                SELECT tenant_id, idempotency_key, status, provider_message_id,
                       web_link, claimed_at, completed_at
                FROM microsoftconnection.outlook_draft_receipts
                WHERE tenant_id = ? AND idempotency_key = ?
                """,
                ROW_MAPPER,
                tenantId,
                idempotencyKey
        ).stream().findFirst();
    }

    @Override
    public boolean tryClaim(UUID tenantId, String idempotencyKey, Instant claimedAt) {
        return jdbc.update(
                """
                INSERT INTO microsoftconnection.outlook_draft_receipts (
                    tenant_id, idempotency_key, status, claimed_at
                ) VALUES (?, ?, 'PENDING', ?)
                ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
                """,
                tenantId,
                idempotencyKey,
                JdbcInstant.toTimestamp(claimedAt)
        ) == 1;
    }

    @Override
    public void complete(
            UUID tenantId,
            String idempotencyKey,
            OutlookDraftResult result,
            Instant completedAt
    ) {
        int updated = jdbc.update(
                """
                UPDATE microsoftconnection.outlook_draft_receipts
                SET status = 'COMPLETED',
                    provider_message_id = ?,
                    web_link = ?,
                    completed_at = ?
                WHERE tenant_id = ?
                  AND idempotency_key = ?
                  AND status = 'PENDING'
                """,
                result.providerMessageId(),
                result.webLink(),
                JdbcInstant.toTimestamp(completedAt),
                tenantId,
                idempotencyKey
        );
        if (updated != 1) {
            throw new IllegalStateException("Draft receipt claim is unavailable");
        }
    }

    @Override
    public void release(UUID tenantId, String idempotencyKey) {
        jdbc.update(
                """
                DELETE FROM microsoftconnection.outlook_draft_receipts
                WHERE tenant_id = ? AND idempotency_key = ? AND status = 'PENDING'
                """,
                tenantId,
                idempotencyKey);
    }
}
