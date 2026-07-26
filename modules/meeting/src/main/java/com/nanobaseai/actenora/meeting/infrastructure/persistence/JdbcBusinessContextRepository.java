package com.nanobaseai.actenora.meeting.infrastructure.persistence;

import com.nanobaseai.actenora.meeting.application.port.BusinessContextRepository;
import com.nanobaseai.actenora.meeting.domain.exception.OptimisticLockConflictException;
import com.nanobaseai.actenora.meeting.domain.model.BusinessContext;
import com.nanobaseai.actenora.meeting.domain.model.BusinessContextStatus;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC business context store ({@code meeting.business_contexts}). */
public final class JdbcBusinessContextRepository implements BusinessContextRepository {

    private static final String COLUMNS = """
            id, tenant_id, type, reference_code, name, description, status, created_at, updated_at, version
            """;

    private static final RowMapper<BusinessContext> ROW_MAPPER = (rs, rowNum) -> BusinessContext.rehydrate(
            rs.getObject("id", UUID.class),
            TenantId.of(rs.getObject("tenant_id", UUID.class)),
            rs.getString("type"),
            rs.getString("reference_code"),
            rs.getString("name"),
            rs.getString("description"),
            BusinessContextStatus.valueOf(rs.getString("status")),
            JdbcInstant.get(rs, "created_at"),
            JdbcInstant.get(rs, "updated_at"),
            rs.getLong("version")
    );

    private final JdbcTemplate jdbc;

    public JdbcBusinessContextRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public BusinessContext save(BusinessContext context) {
        if (context.version() == 0L) {
            insert(context);
            return context;
        }
        long previousVersion = context.version() - 1L;
        int updated = jdbc.update(
                """
                        UPDATE meeting.business_contexts SET
                            type = ?, reference_code = ?, name = ?, description = ?, status = ?,
                            updated_at = ?, version = ?
                        WHERE id = ? AND tenant_id = ? AND version = ?
                        """,
                context.type(),
                context.referenceCode(),
                context.name(),
                context.description(),
                context.status().name(),
                JdbcInstant.toTimestamp(context.updatedAt()),
                context.version(),
                context.id(),
                context.tenantId().value(),
                previousVersion
        );
        if (updated != 1) {
            throw new OptimisticLockConflictException(context.id(), previousVersion);
        }
        return context;
    }

    @Override
    public Optional<BusinessContext> findByIdAndTenantId(UUID id, TenantId tenantId) {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM meeting.business_contexts WHERE id = ? AND tenant_id = ?",
                ROW_MAPPER,
                id,
                tenantId.value()
        ).stream().findFirst();
    }

    @Override
    public Optional<BusinessContext> findByTenantIdAndReferenceCode(TenantId tenantId, String referenceCode) {
        if (referenceCode == null || referenceCode.isBlank()) {
            return Optional.empty();
        }
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM meeting.business_contexts WHERE tenant_id = ? AND lower(reference_code) = lower(?)",
                ROW_MAPPER,
                tenantId.value(),
                referenceCode.trim()
        ).stream().findFirst();
    }

    @Override
    public List<BusinessContext> listByTenantId(TenantId tenantId) {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM meeting.business_contexts WHERE tenant_id = ? ORDER BY created_at",
                ROW_MAPPER,
                tenantId.value()
        );
    }

    private void insert(BusinessContext context) {
        jdbc.update(
                """
                        INSERT INTO meeting.business_contexts (
                            id, tenant_id, type, reference_code, name, description, status,
                            created_at, updated_at, version
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                context.id(),
                context.tenantId().value(),
                context.type(),
                context.referenceCode(),
                context.name(),
                context.description(),
                context.status().name(),
                JdbcInstant.toTimestamp(context.createdAt()),
                JdbcInstant.toTimestamp(context.updatedAt()),
                context.version()
        );
    }
}
