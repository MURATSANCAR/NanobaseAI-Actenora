package com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence;

import com.nanobaseai.actenora.meetingintelligence.application.validation.port.ManualReviewCaseRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ManualReviewCase;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ManualReviewStatus;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcManualReviewCaseRepository implements ManualReviewCaseRepository {

    private static final RowMapper<ManualReviewCase> ROW_MAPPER = (rs, rowNum) -> ManualReviewCase.rehydrate(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getObject("validation_run_id", UUID.class),
            rs.getObject("quality_gate_decision_id", UUID.class),
            rs.getObject("meeting_occurrence_id", UUID.class),
            rs.getString("reason"),
            ManualReviewStatus.valueOf(rs.getString("status")),
            rs.getString("resolved_by"),
            rs.getString("resolution_note"),
            JdbcInstant.get(rs, "created_at"),
            JdbcInstant.get(rs, "resolved_at")
    );

    private final JdbcTemplate jdbc;

    public JdbcManualReviewCaseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public ManualReviewCase save(ManualReviewCase reviewCase) {
        jdbc.update("""
                        INSERT INTO meetingintelligence.manual_review_cases (
                            id, tenant_id, validation_run_id, quality_gate_decision_id,
                            meeting_occurrence_id, reason, status, resolved_by, resolution_note,
                            created_at, resolved_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (id) DO UPDATE SET
                            status = EXCLUDED.status,
                            resolved_by = EXCLUDED.resolved_by,
                            resolution_note = EXCLUDED.resolution_note,
                            resolved_at = EXCLUDED.resolved_at
                        """,
                reviewCase.id(),
                reviewCase.tenantId(),
                reviewCase.validationRunId(),
                reviewCase.qualityGateDecisionId(),
                reviewCase.meetingOccurrenceId(),
                reviewCase.reason(),
                reviewCase.status().name(),
                reviewCase.resolvedBy().orElse(null),
                reviewCase.resolutionNote().orElse(null),
                JdbcInstant.toTimestamp(reviewCase.createdAt()),
                reviewCase.resolvedAt().map(JdbcInstant::toTimestamp).orElse(null)
        );
        return reviewCase;
    }

    @Override
    public Optional<ManualReviewCase> findById(UUID tenantId, UUID caseId) {
        return jdbc.query("""
                        SELECT id, tenant_id, validation_run_id, quality_gate_decision_id,
                               meeting_occurrence_id, reason, status, resolved_by, resolution_note,
                               created_at, resolved_at
                        FROM meetingintelligence.manual_review_cases
                        WHERE tenant_id = ? AND id = ?
                        """,
                ROW_MAPPER, tenantId, caseId
        ).stream().findFirst();
    }

    @Override
    public Optional<ManualReviewCase> findOpenByDecision(UUID tenantId, UUID qualityGateDecisionId) {
        return jdbc.query("""
                        SELECT id, tenant_id, validation_run_id, quality_gate_decision_id,
                               meeting_occurrence_id, reason, status, resolved_by, resolution_note,
                               created_at, resolved_at
                        FROM meetingintelligence.manual_review_cases
                        WHERE tenant_id = ? AND quality_gate_decision_id = ? AND status = ?
                        """,
                ROW_MAPPER, tenantId, qualityGateDecisionId, ManualReviewStatus.OPEN.name()
        ).stream().findFirst();
    }

    @Override
    public List<ManualReviewCase> findByTenant(UUID tenantId, ManualReviewStatus status) {
        return jdbc.query("""
                        SELECT id, tenant_id, validation_run_id, quality_gate_decision_id,
                               meeting_occurrence_id, reason, status, resolved_by, resolution_note,
                               created_at, resolved_at
                        FROM meetingintelligence.manual_review_cases
                        WHERE tenant_id = ? AND status = ?
                        ORDER BY created_at
                        """,
                ROW_MAPPER, tenantId, status.name()
        );
    }
}
