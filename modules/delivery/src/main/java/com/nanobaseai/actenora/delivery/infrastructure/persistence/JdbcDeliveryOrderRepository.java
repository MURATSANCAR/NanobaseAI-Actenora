package com.nanobaseai.actenora.delivery.infrastructure.persistence;

import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.delivery.application.port.DeliveryOrderRepository;
import com.nanobaseai.actenora.delivery.domain.DeliveryOrder;
import com.nanobaseai.actenora.delivery.domain.DeliveryOrderStatus;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Optional;
import java.util.UUID;

/** JDBC delivery order store ({@code delivery.orders}, V212). */
public final class JdbcDeliveryOrderRepository implements DeliveryOrderRepository {

    private static final RowMapper<DeliveryOrder> ROW_MAPPER = (rs, rowNum) -> DeliveryOrder.rehydrate(
            rs.getObject("id", UUID.class),
            TenantId.of(rs.getObject("tenant_id", UUID.class)),
            ApprovalId.of(rs.getObject("approval_id", UUID.class)),
            rs.getObject("note_version_id", UUID.class),
            rs.getString("channel"),
            DeliveryOrderStatus.valueOf(rs.getString("status")),
            JdbcInstant.get(rs, "created_at")
    );

    private final JdbcTemplate jdbc;

    public JdbcDeliveryOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public DeliveryOrder save(DeliveryOrder order) {
        String sql = """
                INSERT INTO delivery.orders (
                    id, tenant_id, approval_id, note_version_id, channel, status, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """;
        jdbc.update(sql,
                order.id(),
                order.tenantId().value(),
                order.approvalId().value(),
                order.noteVersionId(),
                order.channel(),
                order.status().name(),
                JdbcInstant.toTimestamp(order.createdAt())
        );
        return order;
    }

    @Override
    public Optional<DeliveryOrder> findById(TenantId tenantId, UUID orderId) {
        String sql = """
                SELECT id, tenant_id, approval_id, note_version_id, channel, status, created_at
                FROM delivery.orders WHERE tenant_id = ? AND id = ?
                """;
        return jdbc.query(sql, ROW_MAPPER, tenantId.value(), orderId).stream().findFirst();
    }

    @Override
    public Optional<DeliveryOrder> findByKey(
            TenantId tenantId,
            ApprovalId approvalId,
            UUID noteVersionId,
            String channel
    ) {
        String sql = """
                SELECT id, tenant_id, approval_id, note_version_id, channel, status, created_at
                FROM delivery.orders
                WHERE tenant_id = ? AND approval_id = ? AND note_version_id = ?
                  AND lower(channel) = lower(?)
                """;
        return jdbc.query(sql, ROW_MAPPER, tenantId.value(), approvalId.value(), noteVersionId, channel)
                .stream()
                .findFirst();
    }
}
