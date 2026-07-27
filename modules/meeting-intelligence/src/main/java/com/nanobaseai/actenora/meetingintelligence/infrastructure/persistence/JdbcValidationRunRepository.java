package com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence;

import com.nanobaseai.actenora.meetingintelligence.application.validation.port.ValidationRunRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.QualityGateDecision;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.QualityGateOutcome;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.RuleVerdict;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRuleResult;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRun;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC persistence for append-only validation runs and quality-gate decisions (V183 tables).
 */
public final class JdbcValidationRunRepository implements ValidationRunRepository {

    private static final RowMapper<ValidationRun> RUN_WITHOUT_RULES = (rs, rowNum) -> ValidationRun.rehydrate(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getObject("meeting_occurrence_id", UUID.class),
            rs.getObject("source_extraction_id", UUID.class),
            List.of(),
            QualityGateOutcome.valueOf(rs.getString("computed_outcome")),
            rs.getString("engine_version"),
            JdbcInstant.get(rs, "created_at")
    );

    private static final RowMapper<QualityGateDecision> DECISION_MAPPER = (rs, rowNum) -> {
        String original = rs.getString("original_outcome");
        return QualityGateDecision.rehydrate(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("validation_run_id", UUID.class),
                QualityGateOutcome.valueOf(rs.getString("outcome")),
                rs.getBoolean("overridden"),
                rs.getString("override_actor"),
                rs.getString("override_reason"),
                original == null || original.isBlank() ? null : QualityGateOutcome.valueOf(original),
                JdbcInstant.get(rs, "decided_at")
        );
    };

    private final JdbcTemplate jdbc;

    public JdbcValidationRunRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public ValidationRun saveRun(ValidationRun run) {
        jdbc.update("""
                        INSERT INTO meetingintelligence.validation_runs (
                            id, tenant_id, meeting_occurrence_id, source_extraction_id,
                            computed_outcome, engine_version, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (id) DO NOTHING
                        """,
                run.id(),
                run.tenantId(),
                run.meetingOccurrenceId(),
                run.sourceExtractionId(),
                run.computedOutcome().name(),
                run.engineVersion(),
                JdbcInstant.toTimestamp(run.createdAt())
        );
        for (ValidationRuleResult rule : run.ruleResults()) {
            jdbc.update("""
                            INSERT INTO meetingintelligence.validation_rule_results (
                                id, validation_run_id, tenant_id, rule_id, rule_version,
                                verdict, message, candidate_key, detail
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            ON CONFLICT (id) DO NOTHING
                            """,
                    rule.id(),
                    run.id(),
                    run.tenantId(),
                    rule.ruleId(),
                    rule.ruleVersion(),
                    rule.verdict().name(),
                    rule.message(),
                    rule.candidateKey().orElse(null),
                    rule.detail().orElse(null)
            );
        }
        return run;
    }

    @Override
    public QualityGateDecision saveDecision(QualityGateDecision decision) {
        jdbc.update("""
                        INSERT INTO meetingintelligence.quality_gate_decisions (
                            id, tenant_id, validation_run_id, outcome, overridden,
                            override_actor, override_reason, original_outcome, decided_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (id) DO UPDATE SET
                            outcome = EXCLUDED.outcome,
                            overridden = EXCLUDED.overridden,
                            override_actor = EXCLUDED.override_actor,
                            override_reason = EXCLUDED.override_reason,
                            original_outcome = EXCLUDED.original_outcome,
                            decided_at = EXCLUDED.decided_at
                        """,
                decision.id(),
                decision.tenantId(),
                decision.validationRunId(),
                decision.outcome().name(),
                decision.overridden(),
                decision.overrideActor().orElse(null),
                decision.overrideReason().orElse(null),
                decision.originalOutcome().map(Enum::name).orElse(null),
                JdbcInstant.toTimestamp(decision.decidedAt())
        );
        return decision;
    }

    @Override
    public Optional<ValidationRun> findRun(UUID tenantId, UUID runId) {
        List<ValidationRun> rows = jdbc.query("""
                        SELECT id, tenant_id, meeting_occurrence_id, source_extraction_id,
                               computed_outcome, engine_version, created_at
                        FROM meetingintelligence.validation_runs
                        WHERE id = ? AND tenant_id = ?
                        """,
                RUN_WITHOUT_RULES, runId, tenantId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        ValidationRun base = rows.getFirst();
        return Optional.of(ValidationRun.rehydrate(
                base.id(),
                base.tenantId(),
                base.meetingOccurrenceId(),
                base.sourceExtractionId(),
                loadRules(base.id()),
                base.computedOutcome(),
                base.engineVersion(),
                base.createdAt()
        ));
    }

    @Override
    public Optional<QualityGateDecision> findDecision(UUID tenantId, UUID decisionId) {
        return jdbc.query("""
                        SELECT id, tenant_id, validation_run_id, outcome, overridden,
                               override_actor, override_reason, original_outcome, decided_at
                        FROM meetingintelligence.quality_gate_decisions
                        WHERE id = ? AND tenant_id = ?
                        """,
                DECISION_MAPPER, decisionId, tenantId).stream().findFirst();
    }

    @Override
    public Optional<QualityGateDecision> findDecisionByRun(UUID tenantId, UUID validationRunId) {
        return jdbc.query("""
                        SELECT id, tenant_id, validation_run_id, outcome, overridden,
                               override_actor, override_reason, original_outcome, decided_at
                        FROM meetingintelligence.quality_gate_decisions
                        WHERE validation_run_id = ? AND tenant_id = ?
                        """,
                DECISION_MAPPER, validationRunId, tenantId).stream().findFirst();
    }

    @Override
    public List<ValidationRun> findRunsByExtraction(UUID tenantId, UUID sourceExtractionId) {
        List<ValidationRun> bases = jdbc.query("""
                        SELECT id, tenant_id, meeting_occurrence_id, source_extraction_id,
                               computed_outcome, engine_version, created_at
                        FROM meetingintelligence.validation_runs
                        WHERE tenant_id = ? AND source_extraction_id = ?
                        ORDER BY created_at
                        """,
                RUN_WITHOUT_RULES, tenantId, sourceExtractionId);
        List<ValidationRun> out = new ArrayList<>(bases.size());
        for (ValidationRun base : bases) {
            out.add(ValidationRun.rehydrate(
                    base.id(),
                    base.tenantId(),
                    base.meetingOccurrenceId(),
                    base.sourceExtractionId(),
                    loadRules(base.id()),
                    base.computedOutcome(),
                    base.engineVersion(),
                    base.createdAt()
            ));
        }
        return out;
    }

    @Override
    public List<ValidationRun> findRunsByTenant(UUID tenantId) {
        List<ValidationRun> bases = jdbc.query("""
                        SELECT id, tenant_id, meeting_occurrence_id, source_extraction_id,
                               computed_outcome, engine_version, created_at
                        FROM meetingintelligence.validation_runs
                        WHERE tenant_id = ?
                        ORDER BY created_at
                        """,
                RUN_WITHOUT_RULES, tenantId);
        List<ValidationRun> out = new ArrayList<>(bases.size());
        for (ValidationRun base : bases) {
            out.add(ValidationRun.rehydrate(
                    base.id(),
                    base.tenantId(),
                    base.meetingOccurrenceId(),
                    base.sourceExtractionId(),
                    loadRules(base.id()),
                    base.computedOutcome(),
                    base.engineVersion(),
                    base.createdAt()
            ));
        }
        return out;
    }

    private List<ValidationRuleResult> loadRules(UUID runId) {
        return jdbc.query("""
                        SELECT id, rule_id, rule_version, verdict, message, candidate_key, detail
                        FROM meetingintelligence.validation_rule_results
                        WHERE validation_run_id = ?
                        ORDER BY rule_id
                        """,
                (rs, rowNum) -> ValidationRuleResult.rehydrate(
                        rs.getObject("id", UUID.class),
                        rs.getString("rule_id"),
                        rs.getString("rule_version"),
                        RuleVerdict.valueOf(rs.getString("verdict")),
                        rs.getString("message"),
                        rs.getString("candidate_key"),
                        rs.getString("detail")
                ),
                runId);
    }
}
