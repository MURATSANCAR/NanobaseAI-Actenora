package com.nanobaseai.actenora.audit.infrastructure.persistence;

import com.nanobaseai.actenora.audit.application.port.AuditEntryStore;
import com.nanobaseai.actenora.audit.domain.AuditEntry;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcJson;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Append-only JDBC audit store ({@code audit.entries}). */
public final class JdbcAuditEntryStore implements AuditEntryStore {

    private static final RowMapper<AuditEntry> ROW_MAPPER = (rs, rowNum) -> {
        UUID actorUuid = rs.getObject("actor_user_id", UUID.class);
        String actorId = actorUuid != null ? actorUuid.toString() : "system";
        String resourceIdText = rs.getString("resource_id");
        UUID resourceId = resourceIdText != null ? UUID.fromString(resourceIdText) : UUID.randomUUID();
        Map<String, Object> metadata = JdbcJson.readMap(rs.getString("metadata_json"));
        return AuditEntry.rehydrate(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                actorId,
                rs.getString("action"),
                rs.getString("resource_type"),
                resourceId,
                metadata,
                JdbcInstant.get(rs, "occurred_at")
        );
    };

    private final JdbcTemplate jdbc;

    public JdbcAuditEntryStore(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public AuditEntry append(AuditEntry entry) {
        String sql = """
                INSERT INTO audit.entries (
                    id, tenant_id, actor_user_id, action, resource_type, resource_id,
                    correlation_id, trace_id, ip_address, user_agent, occurred_at, metadata_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """;
        UUID actorUuid = parseActorUuid(entry.actorId());
        try {
            jdbc.update(sql,
                    entry.id(),
                    entry.tenantId(),
                    actorUuid,
                    entry.action(),
                    entry.resourceType(),
                    entry.resourceId().toString(),
                    null,
                    null,
                    null,
                    null,
                    JdbcInstant.toTimestamp(entry.occurredAt()),
                    JdbcJson.write(entry.metadata())
            );
        } catch (DuplicateKeyException ex) {
            throw new IllegalStateException("Audit entry already exists: " + entry.id(), ex);
        }
        return entry;
    }

    @Override
    public List<AuditEntry> timeline(UUID tenantId, UUID resourceId) {
        String sql = """
                SELECT id, tenant_id, actor_user_id, action, resource_type, resource_id,
                       metadata_json, occurred_at
                FROM audit.entries
                WHERE tenant_id = ? AND resource_id = ?
                ORDER BY occurred_at ASC
                """;
        return jdbc.query(sql, ROW_MAPPER, tenantId, resourceId.toString());
    }

    @Override
    public List<AuditEntry> listByTenant(UUID tenantId) {
        String sql = """
                SELECT id, tenant_id, actor_user_id, action, resource_type, resource_id,
                       metadata_json, occurred_at
                FROM audit.entries
                WHERE tenant_id = ?
                ORDER BY occurred_at ASC
                """;
        return jdbc.query(sql, ROW_MAPPER, tenantId);
    }

    private static UUID parseActorUuid(String actorId) {
        if (actorId == null || actorId.isBlank() || "system".equalsIgnoreCase(actorId)) {
            return null;
        }
        try {
            return UUID.fromString(actorId);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
