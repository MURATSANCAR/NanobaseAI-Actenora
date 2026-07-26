package com.nanobaseai.actenora.meetingintelligence.infrastructure.ledger;

import com.nanobaseai.actenora.meetingintelligence.application.ledger.port.LedgerProjectionRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.projection.LedgerProjectionState;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC ledger projection store backed by {@code meetingintelligence.meeting_briefs}.
 * Uses a deterministic {@code brief_id} per tenant for the tenant-wide projection snapshot.
 */
public final class JdbcLedgerProjectionRepository implements LedgerProjectionRepository {

    private final JdbcTemplate jdbc;

    public JdbcLedgerProjectionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public Optional<LedgerProjectionState> find(TenantId tenantId) {
        String sql = """
                SELECT payload_json FROM meetingintelligence.meeting_briefs
                WHERE brief_id = ? AND tenant_id = ?
                """;
        return jdbc.query(sql, (rs, rowNum) -> LedgerProjectionJsonCodec.read(tenantId, rs.getString("payload_json")),
                        briefIdForTenant(tenantId), tenantId.value())
                .stream()
                .findFirst();
    }

    @Override
    public LedgerProjectionState getOrCreate(TenantId tenantId) {
        return find(tenantId).orElseGet(() -> new LedgerProjectionState(tenantId));
    }

    @Override
    public void save(LedgerProjectionState state) {
        upsert(state, Instant.now());
    }

    @Override
    public void replace(TenantId tenantId, LedgerProjectionState rebuilt) {
        upsert(rebuilt, Instant.now());
    }

    private void upsert(LedgerProjectionState state, Instant generatedAt) {
        UUID briefId = briefIdForTenant(state.tenantId());
        String sql = """
                INSERT INTO meetingintelligence.meeting_briefs (
                    brief_id, tenant_id, target_occurrence_id, payload_json, generated_at
                ) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (brief_id) DO UPDATE SET
                    payload_json = EXCLUDED.payload_json,
                    generated_at = EXCLUDED.generated_at
                """;
        jdbc.update(sql,
                briefId,
                state.tenantId().value(),
                state.tenantId().value(),
                LedgerProjectionJsonCodec.write(state),
                JdbcInstant.toTimestamp(generatedAt)
        );
    }

    static UUID briefIdForTenant(TenantId tenantId) {
        return UUID.nameUUIDFromBytes(
                ("actenora:ledger-projection:" + tenantId.value()).getBytes(StandardCharsets.UTF_8)
        );
    }
}
