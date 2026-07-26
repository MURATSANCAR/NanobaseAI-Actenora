package com.nanobaseai.actenora.approval.infrastructure.persistence;

import com.nanobaseai.actenora.approval.application.port.ApprovalRequestRepository;
import com.nanobaseai.actenora.approval.domain.ApprovalDecision;
import com.nanobaseai.actenora.approval.domain.ApprovalRequest;
import com.nanobaseai.actenora.approval.domain.ApprovalStep;
import com.nanobaseai.actenora.approval.domain.ApprovalStepStatus;
import com.nanobaseai.actenora.approval.domain.ChangeRequest;
import com.nanobaseai.actenora.approval.domain.ChangeRequestStatus;
import com.nanobaseai.actenora.approval.domain.exception.OptimisticLockConflictException;
import com.nanobaseai.actenora.approval.api.ApprovalDecisionType;
import com.nanobaseai.actenora.approval.api.ApprovalRequestStatus;
import com.nanobaseai.actenora.approval.api.ApprovalSubjectType;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC approval request store (4 tables in {@code approval.*}, V191). */
public final class JdbcApprovalRequestRepository implements ApprovalRequestRepository {

    private final JdbcTemplate jdbc;

    public JdbcApprovalRequestRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public ApprovalRequest save(ApprovalRequest request) {
        jdbc.execute((Connection conn) -> {
            conn.setAutoCommit(false);
            try {
                persistRequest(conn, request);
                replaceSteps(conn, request);
                replaceDecisions(conn, request);
                replaceChangeRequests(conn, request);
                conn.commit();
                return null;
            } catch (RuntimeException ex) {
                conn.rollback();
                throw ex;
            } catch (SQLException ex) {
                conn.rollback();
                throw new IllegalStateException("Failed to save approval request " + request.id(), ex);
            }
        });
        return request;
    }

    @Override
    public Optional<ApprovalRequest> findById(TenantId tenantId, UUID approvalRequestId) {
        String sql = """
                SELECT id, tenant_id, subject_type, subject_id, status, created_at, updated_at, expires_at, version
                FROM approval.approval_requests WHERE id = ? AND tenant_id = ?
                """;
        List<ApprovalRequest> rows = jdbc.query(sql, (rs, rowNum) -> ApprovalRequest.rehydrate(
                rs.getObject("id", UUID.class),
                TenantId.of(rs.getObject("tenant_id", UUID.class)),
                ApprovalSubjectType.valueOf(rs.getString("subject_type")),
                rs.getObject("subject_id", UUID.class),
                ApprovalRequestStatus.valueOf(rs.getString("status")),
                JdbcInstant.get(rs, "created_at"),
                JdbcInstant.get(rs, "updated_at"),
                JdbcInstant.get(rs, "expires_at"),
                rs.getLong("version"),
                List.of(),
                List.of(),
                List.of()
        ), approvalRequestId, tenantId.value());
        return rows.stream().findFirst().map(this::hydrateChildren);
    }

    @Override
    public Optional<ApprovalRequest> findBySubject(TenantId tenantId, UUID subjectId) {
        String sql = """
                SELECT id FROM approval.approval_requests WHERE tenant_id = ? AND subject_id = ?
                ORDER BY created_at DESC LIMIT 1
                """;
        List<UUID> ids = jdbc.query(sql, (rs, rowNum) -> rs.getObject("id", UUID.class), tenantId.value(), subjectId);
        return ids.stream().findFirst().flatMap(id -> findById(tenantId, id));
    }

    private ApprovalRequest hydrateChildren(ApprovalRequest shell) {
        List<ApprovalStep> steps = loadSteps(shell.id());
        List<ApprovalDecision> decisions = loadDecisions(shell.id());
        List<ChangeRequest> changeRequests = loadChangeRequests(shell.id());
        return ApprovalRequest.rehydrate(
                shell.id(), shell.tenantId(), shell.subjectType(), shell.subjectId(), shell.status(),
                shell.createdAt(), shell.updatedAt(), shell.expiresAt(), shell.version(),
                steps, decisions, changeRequests
        );
    }

    private void persistRequest(Connection conn, ApprovalRequest request) throws SQLException {
        if (request.version() == 0L) {
            insertRequest(conn, request);
            return;
        }
        long previousVersion = request.version() - 1L;
        String sql = """
                UPDATE approval.approval_requests SET
                    status = ?, updated_at = ?, expires_at = ?, version = ?
                WHERE id = ? AND version = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, request.status().name());
            ps.setTimestamp(2, JdbcInstant.toTimestamp(request.updatedAt()));
            ps.setTimestamp(3, JdbcInstant.toTimestamp(request.expiresAt()));
            ps.setLong(4, request.version());
            ps.setObject(5, request.id());
            ps.setLong(6, previousVersion);
            if (ps.executeUpdate() == 1) {
                return;
            }
        }
        throw new OptimisticLockConflictException(request.id(), previousVersion);
    }

    private void insertRequest(Connection conn, ApprovalRequest request) throws SQLException {
        String sql = """
                INSERT INTO approval.approval_requests (
                    id, tenant_id, subject_type, subject_id, status, created_at, updated_at, expires_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, request.id());
            ps.setObject(2, request.tenantId().value());
            ps.setString(3, request.subjectType().name());
            ps.setObject(4, request.subjectId());
            ps.setString(5, request.status().name());
            ps.setTimestamp(6, JdbcInstant.toTimestamp(request.createdAt()));
            ps.setTimestamp(7, JdbcInstant.toTimestamp(request.updatedAt()));
            ps.setTimestamp(8, JdbcInstant.toTimestamp(request.expiresAt()));
            ps.setLong(9, request.version());
            ps.executeUpdate();
        }
    }

    private void replaceSteps(Connection conn, ApprovalRequest request) throws SQLException {
        try (PreparedStatement delete = conn.prepareStatement(
                "DELETE FROM approval.approval_steps WHERE approval_request_id = ?"
        )) {
            delete.setObject(1, request.id());
            delete.executeUpdate();
        }
        String sql = """
                INSERT INTO approval.approval_steps (
                    id, approval_request_id, step_order, required_approver_id, status, decided_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (ApprovalStep step : request.steps()) {
                ps.setObject(1, step.id());
                ps.setObject(2, request.id());
                ps.setInt(3, step.stepOrder());
                ps.setString(4, step.requiredApproverId());
                ps.setString(5, step.status().name());
                ps.setTimestamp(6, JdbcInstant.toTimestamp(step.decidedAt()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void replaceDecisions(Connection conn, ApprovalRequest request) throws SQLException {
        try (PreparedStatement delete = conn.prepareStatement(
                "DELETE FROM approval.approval_decisions WHERE approval_request_id = ?"
        )) {
            delete.setObject(1, request.id());
            delete.executeUpdate();
        }
        String sql = """
                INSERT INTO approval.approval_decisions (
                    id, approval_request_id, step_id, decision_type, decided_by, comment, decided_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (ApprovalDecision decision : request.decisions()) {
                ps.setObject(1, decision.id());
                ps.setObject(2, request.id());
                ps.setObject(3, decision.stepId());
                ps.setString(4, decision.decisionType().name());
                ps.setString(5, decision.decidedBy());
                ps.setString(6, decision.comment());
                ps.setTimestamp(7, JdbcInstant.toTimestamp(decision.decidedAt()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void replaceChangeRequests(Connection conn, ApprovalRequest request) throws SQLException {
        try (PreparedStatement delete = conn.prepareStatement(
                "DELETE FROM approval.change_requests WHERE approval_request_id = ?"
        )) {
            delete.setObject(1, request.id());
            delete.executeUpdate();
        }
        String sql = """
                INSERT INTO approval.change_requests (
                    id, approval_request_id, subject_id, requested_by, reason, status, created_at, resolved_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (ChangeRequest changeRequest : request.changeRequests()) {
                ps.setObject(1, changeRequest.id());
                ps.setObject(2, request.id());
                ps.setObject(3, changeRequest.subjectId());
                ps.setString(4, changeRequest.requestedBy());
                ps.setString(5, changeRequest.reason());
                ps.setString(6, changeRequest.status().name());
                ps.setTimestamp(7, JdbcInstant.toTimestamp(changeRequest.createdAt()));
                ps.setTimestamp(8, JdbcInstant.toTimestamp(changeRequest.resolvedAt()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private List<ApprovalStep> loadSteps(UUID requestId) {
        return jdbc.query(
                """
                        SELECT id, step_order, required_approver_id, status, decided_at
                        FROM approval.approval_steps WHERE approval_request_id = ?
                        ORDER BY step_order
                        """,
                (rs, rowNum) -> ApprovalStep.rehydrate(
                        rs.getObject("id", UUID.class),
                        rs.getInt("step_order"),
                        rs.getString("required_approver_id"),
                        ApprovalStepStatus.valueOf(rs.getString("status")),
                        JdbcInstant.get(rs, "decided_at")
                ),
                requestId
        );
    }

    private List<ApprovalDecision> loadDecisions(UUID requestId) {
        return jdbc.query(
                """
                        SELECT id, approval_request_id, step_id, decision_type, decided_by, comment, decided_at
                        FROM approval.approval_decisions WHERE approval_request_id = ?
                        """,
                (rs, rowNum) -> ApprovalDecision.rehydrate(
                        rs.getObject("id", UUID.class),
                        rs.getObject("approval_request_id", UUID.class),
                        rs.getObject("step_id", UUID.class),
                        ApprovalDecisionType.valueOf(rs.getString("decision_type")),
                        rs.getString("decided_by"),
                        rs.getString("comment"),
                        JdbcInstant.get(rs, "decided_at")
                ),
                requestId
        );
    }

    private List<ChangeRequest> loadChangeRequests(UUID requestId) {
        return jdbc.query(
                """
                        SELECT id, approval_request_id, subject_id, requested_by, reason, status, created_at, resolved_at
                        FROM approval.change_requests WHERE approval_request_id = ?
                        """,
                (rs, rowNum) -> ChangeRequest.rehydrate(
                        rs.getObject("id", UUID.class),
                        rs.getObject("approval_request_id", UUID.class),
                        rs.getObject("subject_id", UUID.class),
                        rs.getString("requested_by"),
                        rs.getString("reason"),
                        ChangeRequestStatus.valueOf(rs.getString("status")),
                        JdbcInstant.get(rs, "created_at"),
                        JdbcInstant.get(rs, "resolved_at")
                ),
                requestId
        );
    }
}
