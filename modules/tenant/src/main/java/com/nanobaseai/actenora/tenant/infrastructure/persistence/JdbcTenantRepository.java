package com.nanobaseai.actenora.tenant.infrastructure.persistence;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.tenant.application.port.TenantRepositoryPort;
import com.nanobaseai.actenora.tenant.domain.OptimisticLockException;
import com.nanobaseai.actenora.tenant.domain.Tenant;
import com.nanobaseai.actenora.tenant.domain.TenantMembership;
import com.nanobaseai.actenora.tenant.domain.TenantStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcTenantRepository implements TenantRepositoryPort {

    private static final String TENANT_COLUMNS = """
            id, name, status, timezone, default_language, retention_policy_days,
            entra_tenant_id, created_at, updated_at, version
            """;

    private static final RowMapper<TenantRow> TENANT_ROW_MAPPER = (rs, rowNum) -> new TenantRow(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            TenantStatus.valueOf(rs.getString("status")),
            rs.getString("timezone"),
            rs.getString("default_language"),
            rs.getInt("retention_policy_days"),
            rs.getString("entra_tenant_id"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            rs.getLong("version")
    );

    private final JdbcTemplate jdbc;

    public JdbcTenantRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public Optional<Tenant> findById(TenantId tenantId) {
        String sql = "SELECT " + TENANT_COLUMNS + " FROM tenant.tenants WHERE id = ?";
        List<TenantRow> rows = jdbc.query(sql, TENANT_ROW_MAPPER, tenantId.value());
        return rows.isEmpty() ? Optional.empty() : Optional.of(toTenant(rows.getFirst()));
    }

    @Override
    public Optional<Tenant> findByEntraTenantId(String entraTenantId) {
        String sql = "SELECT " + TENANT_COLUMNS + " FROM tenant.tenants WHERE entra_tenant_id = ?";
        List<TenantRow> rows = jdbc.query(sql, TENANT_ROW_MAPPER, entraTenantId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(toTenant(rows.getFirst()));
    }

    @Override
    public void save(Tenant tenant) {
        if (tenant.version() == 0L) {
            insertTenant(tenant);
            return;
        }

        long previousVersion = tenant.version() - 1L;
        String updateSql = """
                UPDATE tenant.tenants SET
                    name = ?,
                    status = ?,
                    timezone = ?,
                    default_language = ?,
                    retention_policy_days = ?,
                    entra_tenant_id = ?,
                    updated_at = ?,
                    version = ?
                WHERE id = ? AND version = ?
                """;
        int updated = jdbc.update(
                updateSql,
                tenant.name(),
                tenant.status().name(),
                tenant.timezone(),
                tenant.defaultLanguage(),
                tenant.retentionPolicyDays(),
                tenant.entraTenantId(),
                Timestamp.from(tenant.updatedAt()),
                tenant.version(),
                tenant.id().value(),
                previousVersion
        );
        if (updated == 1) {
            return;
        }

        long actualVersion = jdbc.query(
                "SELECT version FROM tenant.tenants WHERE id = ?",
                rs -> rs.next() ? rs.getLong("version") : -1L,
                tenant.id().value()
        );
        throw new OptimisticLockException(tenant.id().value(), previousVersion, actualVersion);
    }

    @Override
    public boolean isMember(TenantId tenantId, UUID userId) {
        Integer count = jdbc.queryForObject(
                """
                        SELECT COUNT(*) FROM tenant.tenant_memberships
                        WHERE tenant_id = ? AND user_id = ?
                        """,
                Integer.class,
                tenantId.value(),
                userId
        );
        return count != null && count > 0;
    }

    @Override
    public void saveMembership(TenantMembership membership) {
        if (isMember(membership.tenantId(), membership.userId())) {
            return;
        }
        jdbc.update(
                """
                        INSERT INTO tenant.tenant_memberships (tenant_id, user_id, created_at)
                        VALUES (?, ?, ?)
                        """,
                membership.tenantId().value(),
                membership.userId(),
                Timestamp.from(Instant.now())
        );
    }

    private void insertTenant(Tenant tenant) {
        String insertSql = """
                INSERT INTO tenant.tenants (
                    id, name, status, timezone, default_language, retention_policy_days,
                    entra_tenant_id, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbc.update(
                insertSql,
                tenant.id().value(),
                tenant.name(),
                tenant.status().name(),
                tenant.timezone(),
                tenant.defaultLanguage(),
                tenant.retentionPolicyDays(),
                tenant.entraTenantId(),
                Timestamp.from(tenant.createdAt()),
                Timestamp.from(tenant.updatedAt()),
                tenant.version()
        );
    }

    private static Tenant toTenant(TenantRow row) {
        return new Tenant(
                TenantId.of(row.id()),
                row.name(),
                row.status(),
                row.timezone(),
                row.defaultLanguage(),
                row.retentionPolicyDays(),
                row.entraTenantId(),
                row.createdAt(),
                row.updatedAt(),
                row.version()
        );
    }

    private record TenantRow(
            UUID id,
            String name,
            TenantStatus status,
            String timezone,
            String defaultLanguage,
            int retentionPolicyDays,
            String entraTenantId,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
    }
}
