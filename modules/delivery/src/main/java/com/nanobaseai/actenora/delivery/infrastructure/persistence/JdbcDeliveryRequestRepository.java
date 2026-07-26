package com.nanobaseai.actenora.delivery.infrastructure.persistence;

import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.delivery.application.port.DeliveryRequestRepository;
import com.nanobaseai.actenora.delivery.domain.DeliveryAttempt;
import com.nanobaseai.actenora.delivery.domain.DeliveryDeadLetter;
import com.nanobaseai.actenora.delivery.domain.DeliveryPolicySnapshot;
import com.nanobaseai.actenora.delivery.domain.DeliveryRecipient;
import com.nanobaseai.actenora.delivery.domain.DeliveryRequest;
import com.nanobaseai.actenora.delivery.domain.DeliveryStatus;
import com.nanobaseai.actenora.delivery.domain.PdfAttachmentDecision;
import com.nanobaseai.actenora.delivery.domain.ProviderMessage;
import com.nanobaseai.actenora.delivery.domain.RecipientKind;
import com.nanobaseai.actenora.delivery.domain.SignedPortalLink;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcJson;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC delivery request store ({@code delivery.requests}, V213). */
public final class JdbcDeliveryRequestRepository implements DeliveryRequestRepository {

    private final JdbcTemplate jdbc;

    public JdbcDeliveryRequestRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public DeliveryRequest save(DeliveryRequest request) {
        jdbc.execute((Connection conn) -> {
            conn.setAutoCommit(false);
            try {
                persistRequest(conn, request);
                replaceAttempts(conn, request);
                conn.commit();
                return null;
            } catch (RuntimeException ex) {
                conn.rollback();
                throw ex;
            } catch (SQLException ex) {
                conn.rollback();
                throw new IllegalStateException("Failed to save delivery request " + request.id(), ex);
            }
        });
        return request;
    }

    @Override
    public Optional<DeliveryRequest> findById(TenantId tenantId, UUID id) {
        return loadRequest("WHERE r.tenant_id = ? AND r.id = ?", tenantId.value(), id);
    }

    @Override
    public Optional<DeliveryRequest> findByNoteVersionAndRecipient(
            TenantId tenantId,
            UUID noteVersionId,
            String recipientEmail
    ) {
        return loadRequest(
                "WHERE r.tenant_id = ? AND r.note_version_id = ? AND lower(r.recipient_email) = lower(?)",
                tenantId.value(),
                noteVersionId,
                recipientEmail.trim()
        );
    }

    @Override
    public Optional<DeliveryRequest> findByProviderMessageId(TenantId tenantId, String providerMessageId) {
        String sql = """
                SELECT r.id FROM delivery.requests r
                JOIN delivery.attempts a ON a.delivery_request_id = r.id
                JOIN delivery.provider_messages pm ON pm.attempt_id = a.id
                WHERE r.tenant_id = ? AND pm.provider_message_id = ?
                LIMIT 1
                """;
        List<UUID> ids = jdbc.query(sql, (rs, rowNum) -> rs.getObject("id", UUID.class), tenantId.value(), providerMessageId);
        return ids.stream().findFirst().flatMap(id -> findById(tenantId, id));
    }

    @Override
    public List<DeliveryRequest> findDue(Instant now, int limit) {
        String sql = """
                SELECT id, tenant_id FROM delivery.requests
                WHERE status = 'QUEUED' AND (next_attempt_at IS NULL OR next_attempt_at <= ?)
                ORDER BY created_at ASC LIMIT ?
                """;
        List<DeliveryRequest> due = new ArrayList<>();
        jdbc.query(sql, rs -> {
            UUID id = rs.getObject("id", UUID.class);
            TenantId tenantId = TenantId.of(rs.getObject("tenant_id", UUID.class));
            findById(tenantId, id).ifPresent(due::add);
        }, JdbcInstant.toTimestamp(now), limit);
        return due;
    }

    @Override
    public void saveDeadLetter(DeliveryDeadLetter deadLetter) {
        String sql = """
                INSERT INTO delivery.dead_letters (
                    id, delivery_request_id, tenant_id, note_version_id, recipient_email,
                    failure_code, failure_detail, attempts, dead_lettered_at, replayed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET replayed_at = EXCLUDED.replayed_at
                """;
        jdbc.update(sql,
                deadLetter.id(),
                deadLetter.deliveryRequestId(),
                deadLetter.tenantId().value(),
                deadLetter.noteVersionId(),
                deadLetter.recipientEmail(),
                deadLetter.failureCode(),
                deadLetter.failureDetail(),
                deadLetter.attempts(),
                JdbcInstant.toTimestamp(deadLetter.deadLetteredAt()),
                deadLetter.replayedAtOptional().map(JdbcInstant::toTimestamp).orElse(null)
        );
    }

    @Override
    public Optional<DeliveryDeadLetter> findDeadLetter(UUID id) {
        String sql = """
                SELECT id, delivery_request_id, tenant_id, note_version_id, recipient_email,
                       failure_code, failure_detail, attempts, dead_lettered_at, replayed_at
                FROM delivery.dead_letters WHERE id = ?
                """;
        return jdbc.query(sql, (rs, rowNum) -> new DeliveryDeadLetter(
                rs.getObject("id", UUID.class),
                rs.getObject("delivery_request_id", UUID.class),
                TenantId.of(rs.getObject("tenant_id", UUID.class)),
                rs.getObject("note_version_id", UUID.class),
                rs.getString("recipient_email"),
                rs.getString("failure_code"),
                rs.getString("failure_detail"),
                rs.getInt("attempts"),
                JdbcInstant.get(rs, "dead_lettered_at"),
                JdbcInstant.get(rs, "replayed_at")
        ), id).stream().findFirst();
    }

    @Override
    public List<DeliveryDeadLetter> listOpenDeadLetters(int limit) {
        String sql = """
                SELECT id, delivery_request_id, tenant_id, note_version_id, recipient_email,
                       failure_code, failure_detail, attempts, dead_lettered_at, replayed_at
                FROM delivery.dead_letters
                WHERE replayed_at IS NULL
                ORDER BY dead_lettered_at ASC LIMIT ?
                """;
        return jdbc.query(sql, (rs, rowNum) -> new DeliveryDeadLetter(
                rs.getObject("id", UUID.class),
                rs.getObject("delivery_request_id", UUID.class),
                TenantId.of(rs.getObject("tenant_id", UUID.class)),
                rs.getObject("note_version_id", UUID.class),
                rs.getString("recipient_email"),
                rs.getString("failure_code"),
                rs.getString("failure_detail"),
                rs.getInt("attempts"),
                JdbcInstant.get(rs, "dead_lettered_at"),
                JdbcInstant.get(rs, "replayed_at")
        ), limit);
    }

    private Optional<DeliveryRequest> loadRequest(String whereClause, Object... args) {
        String sql = """
                SELECT r.id, r.tenant_id, r.note_version_id, r.approval_id, r.recipient_id, r.recipient_email,
                       r.recipient_kind, r.recipient_name, r.status, r.subject, r.body_text, r.policy_snapshot,
                       r.pdf_attach, r.pdf_document_id, r.pdf_object_key, r.signed_portal_url, r.signed_portal_exp,
                       r.signed_portal_fp, r.next_attempt_at, r.dead_letter_id, r.created_at, r.updated_at
                FROM delivery.requests r
                """ + whereClause;
        List<DeliveryRequest> rows = jdbc.query(sql, (rs, rowNum) -> mapRequestShell(rs), args);
        return rows.stream().findFirst().map(shell -> hydrateAttempts(shell));
    }

    private DeliveryRequest mapRequestShell(java.sql.ResultSet rs) throws java.sql.SQLException {
        DeliveryPolicySnapshot policy = JdbcJson.read(rs.getString("policy_snapshot"), DeliveryPolicySnapshot.class);
        DeliveryRecipient recipient = new DeliveryRecipient(
                rs.getObject("recipient_id", UUID.class),
                rs.getString("recipient_email"),
                RecipientKind.valueOf(rs.getString("recipient_kind")),
                rs.getString("recipient_name")
        );
        PdfAttachmentDecision pdf = null;
        if (rs.getBoolean("pdf_attach")) {
            pdf = PdfAttachmentDecision.attach(
                    rs.getObject("pdf_document_id", UUID.class),
                    rs.getString("pdf_object_key")
            );
        }
        SignedPortalLink link = null;
        String portalUrl = rs.getString("signed_portal_url");
        if (portalUrl != null) {
            link = new SignedPortalLink(
                    URI.create(portalUrl),
                    JdbcInstant.get(rs, "signed_portal_exp"),
                    rs.getString("signed_portal_fp")
            );
        }
        return DeliveryRequest.rehydrate(
                rs.getObject("id", UUID.class),
                TenantId.of(rs.getObject("tenant_id", UUID.class)),
                rs.getObject("note_version_id", UUID.class),
                ApprovalId.of(rs.getObject("approval_id", UUID.class)),
                recipient,
                policy,
                DeliveryStatus.valueOf(rs.getString("status")),
                JdbcInstant.get(rs, "created_at"),
                JdbcInstant.get(rs, "updated_at"),
                JdbcInstant.get(rs, "next_attempt_at"),
                List.of(),
                pdf,
                link,
                rs.getString("subject"),
                rs.getString("body_text"),
                rs.getObject("dead_letter_id", UUID.class)
        );
    }

    private DeliveryRequest hydrateAttempts(DeliveryRequest shell) {
        List<DeliveryAttempt> attempts = loadAttempts(shell.id());
        return DeliveryRequest.rehydrate(
                shell.id(), shell.tenantId(), shell.noteVersionId(), shell.approvalId(), shell.recipient(),
                shell.policySnapshot(), shell.status(), shell.createdAt(), shell.updatedAt(),
                shell.nextAttemptAt().orElse(null), attempts,
                shell.pdfAttachment().orElse(null),
                shell.signedPortalLink().orElse(null),
                shell.subject(), shell.bodyText(), shell.deadLetterId().orElse(null)
        );
    }

    private List<DeliveryAttempt> loadAttempts(UUID requestId) {
        String sql = """
                SELECT a.id, a.attempt_number, a.status, a.started_at, a.finished_at,
                       a.failure_code, a.failure_detail,
                       pm.id AS pm_id, pm.provider_type, pm.provider_message_id, pm.acceptance_status,
                       pm.accepted_at, pm.delivered_at, pm.raw_status_code
                FROM delivery.attempts a
                LEFT JOIN delivery.provider_messages pm ON pm.attempt_id = a.id
                WHERE a.delivery_request_id = ?
                ORDER BY a.attempt_number
                """;
        return jdbc.query(sql, (rs, rowNum) -> {
            ProviderMessage message = null;
            UUID pmId = rs.getObject("pm_id", UUID.class);
            if (pmId != null) {
                message = new ProviderMessage(
                        pmId,
                        rs.getString("provider_type"),
                        rs.getString("provider_message_id"),
                        ProviderMessage.ProviderAcceptanceStatus.valueOf(rs.getString("acceptance_status")),
                        JdbcInstant.get(rs, "accepted_at"),
                        JdbcInstant.get(rs, "delivered_at"),
                        rs.getString("raw_status_code")
                );
            }
            return DeliveryAttempt.rehydrate(
                    rs.getObject("id", UUID.class),
                    rs.getInt("attempt_number"),
                    DeliveryStatus.valueOf(rs.getString("status")),
                    JdbcInstant.get(rs, "started_at"),
                    JdbcInstant.get(rs, "finished_at"),
                    rs.getString("failure_code"),
                    rs.getString("failure_detail"),
                    message
            );
        }, requestId);
    }

    private void persistRequest(Connection conn, DeliveryRequest request) throws SQLException {
        String sql = """
                INSERT INTO delivery.requests (
                    id, tenant_id, note_version_id, approval_id, recipient_id, recipient_email,
                    recipient_kind, recipient_name, status, subject, body_text, policy_snapshot,
                    pdf_attach, pdf_document_id, pdf_object_key, signed_portal_url, signed_portal_exp,
                    signed_portal_fp, next_attempt_at, dead_letter_id, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    status = EXCLUDED.status,
                    next_attempt_at = EXCLUDED.next_attempt_at,
                    dead_letter_id = EXCLUDED.dead_letter_id,
                    updated_at = EXCLUDED.updated_at
                """;
        PdfAttachmentDecision pdf = request.pdfAttachment().orElse(null);
        SignedPortalLink link = request.signedPortalLink().orElse(null);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, request.id());
            ps.setObject(2, request.tenantId().value());
            ps.setObject(3, request.noteVersionId());
            ps.setObject(4, request.approvalId().value());
            ps.setObject(5, request.recipient().id());
            ps.setString(6, request.recipient().email());
            ps.setString(7, request.recipient().kind().name());
            ps.setString(8, request.recipient().displayName());
            ps.setString(9, request.status().name());
            ps.setString(10, request.subject());
            ps.setString(11, request.bodyText());
            ps.setString(12, JdbcJson.write(request.policySnapshot()));
            ps.setBoolean(13, pdf != null && pdf.attach());
            ps.setObject(14, pdf == null ? null : pdf.renderedDocumentIdOptional().orElse(null));
            ps.setString(15, pdf == null ? null : pdf.objectKeyOptional().orElse(null));
            ps.setString(16, link == null ? null : link.url().toString());
            ps.setTimestamp(17, link == null ? null : JdbcInstant.toTimestamp(link.expiresAt()));
            ps.setString(18, link == null ? null : link.tokenFingerprint());
            ps.setTimestamp(19, request.nextAttemptAt().map(JdbcInstant::toTimestamp).orElse(null));
            ps.setObject(20, request.deadLetterId().orElse(null));
            ps.setTimestamp(21, JdbcInstant.toTimestamp(request.createdAt()));
            ps.setTimestamp(22, JdbcInstant.toTimestamp(request.updatedAt()));
            ps.executeUpdate();
        }
    }

    private void replaceAttempts(Connection conn, DeliveryRequest request) throws SQLException {
        try (PreparedStatement deleteMessages = conn.prepareStatement(
                """
                        DELETE FROM delivery.provider_messages WHERE attempt_id IN (
                            SELECT id FROM delivery.attempts WHERE delivery_request_id = ?
                        )
                        """
        )) {
            deleteMessages.setObject(1, request.id());
            deleteMessages.executeUpdate();
        }
        try (PreparedStatement deleteAttempts = conn.prepareStatement(
                "DELETE FROM delivery.attempts WHERE delivery_request_id = ?"
        )) {
            deleteAttempts.setObject(1, request.id());
            deleteAttempts.executeUpdate();
        }
        String attemptSql = """
                INSERT INTO delivery.attempts (
                    id, delivery_request_id, attempt_number, status, started_at, finished_at,
                    failure_code, failure_detail
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String messageSql = """
                INSERT INTO delivery.provider_messages (
                    id, attempt_id, provider_type, provider_message_id, acceptance_status,
                    accepted_at, delivered_at, raw_status_code
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement attemptPs = conn.prepareStatement(attemptSql);
             PreparedStatement messagePs = conn.prepareStatement(messageSql)) {
            for (DeliveryAttempt attempt : request.attempts()) {
                attemptPs.setObject(1, attempt.id());
                attemptPs.setObject(2, request.id());
                attemptPs.setInt(3, attempt.attemptNumber());
                attemptPs.setString(4, attempt.status().name());
                attemptPs.setTimestamp(5, JdbcInstant.toTimestamp(attempt.startedAt()));
                attemptPs.setTimestamp(6, attempt.finishedAt().map(JdbcInstant::toTimestamp).orElse(null));
                attemptPs.setString(7, attempt.failureCode().orElse(null));
                attemptPs.setString(8, attempt.failureDetail().orElse(null));
                attemptPs.addBatch();
                attempt.providerMessage().ifPresent(message -> {
                    try {
                        messagePs.setObject(1, message.id());
                        messagePs.setObject(2, attempt.id());
                        messagePs.setString(3, message.providerType());
                        messagePs.setString(4, message.providerMessageId());
                        messagePs.setString(5, message.acceptanceStatus().name());
                        messagePs.setTimestamp(6, JdbcInstant.toTimestamp(message.acceptedAt()));
                        messagePs.setTimestamp(7, message.deliveredAtOptional().map(JdbcInstant::toTimestamp).orElse(null));
                        messagePs.setString(8, message.rawStatusCode());
                        messagePs.addBatch();
                    } catch (SQLException ex) {
                        throw new IllegalStateException("Failed to bind provider message", ex);
                    }
                });
            }
            attemptPs.executeBatch();
            messagePs.executeBatch();
        }
    }
}
