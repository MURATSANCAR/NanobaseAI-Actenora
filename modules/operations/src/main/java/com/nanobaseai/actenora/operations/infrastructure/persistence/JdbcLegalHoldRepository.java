package com.nanobaseai.actenora.operations.infrastructure.persistence;

import com.nanobaseai.actenora.operations.application.port.LegalHoldRepository;
import com.nanobaseai.actenora.operations.domain.retention.LegalHold;
import com.nanobaseai.actenora.operations.domain.retention.RetentionResourceType;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC legal hold store ({@code operations.legal_holds}). */
public final class JdbcLegalHoldRepository implements LegalHoldRepository {

    private static final RowMapper<LegalHold> ROW_MAPPER = (rs, rowNum) -> LegalHold.rehydrate(
            rs.getObject("id", UUID.class),
            TenantId.of(rs.getObject("tenant_id", UUID.class)),
            RetentionResourceType.valueOf(rs.getString("resource_type")),
            rs.getString("resource_id"),
            rs.getString("reason"),
            rs.getObject("placed_by_user_id", UUID.class),
            JdbcInstant.get(rs, "placed_at"),
            JdbcInstant.get(rs, "released_at")
    );

    private final JdbcTemplate jdbc;

    public JdbcLegalHoldRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public LegalHold save(LegalHold hold) {
        if (findById(hold.id()).isEmpty()) {
            insert(hold);
        } else {
            update(hold);
        }
        return hold;
    }

    @Override
    public Optional<LegalHold> findById(UUID id) {
        String sql = """
                SELECT id, tenant_id, resource_type, resource_id, reason,
                       placed_by_user_id, placed_at, released_at
                FROM operations.legal_holds WHERE id = ?
                """;
        return jdbc.query(sql, ROW_MAPPER, id).stream().findFirst();
    }

    @Override
    public List<LegalHold> findActiveForResource(
            TenantId tenantId,
            RetentionResourceType resourceType,
            String resourceId
    ) {
        String sql = """
                SELECT id, tenant_id, resource_type, resource_id, reason,
                       placed_by_user_id, placed_at, released_at
                FROM operations.legal_holds
                WHERE tenant_id = ? AND resource_type = ? AND resource_id = ?
                  AND released_at IS NULL
                """;
        return jdbc.query(sql, ROW_MAPPER, tenantId.value(), resourceType.name(), resourceId);
    }

    @Override
    public List<LegalHold> findActiveForTenant(TenantId tenantId) {
        String sql = """
                SELECT id, tenant_id, resource_type, resource_id, reason,
                       placed_by_user_id, placed_at, released_at
                FROM operations.legal_holds
                WHERE tenant_id = ? AND released_at IS NULL
                """;
        return jdbc.query(sql, ROW_MAPPER, tenantId.value());
    }

    private void insert(LegalHold hold) {
        String sql = """
                INSERT INTO operations.legal_holds (
                    id, tenant_id, resource_type, resource_id, reason,
                    placed_by_user_id, placed_at, released_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbc.update(sql,
                hold.id(),
                hold.tenantId().value(),
                hold.resourceType().name(),
                hold.resourceId(),
                hold.reason(),
                hold.placedByUserId().orElse(null),
                JdbcInstant.toTimestamp(hold.placedAt()),
                hold.releasedAt().map(JdbcInstant::toTimestamp).orElse(null)
        );
    }

    private void update(LegalHold hold) {
        String sql = """
                UPDATE operations.legal_holds SET
                    released_at = ?
                WHERE id = ?
                """;
        jdbc.update(sql,
                hold.releasedAt().map(JdbcInstant::toTimestamp).orElse(null),
                hold.id()
        );
    }
}
