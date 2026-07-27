package com.nanobaseai.actenora.aiprocessing.infrastructure.persistence;

import com.nanobaseai.actenora.aiprocessing.application.port.RoutingDecisionStorePort;
import com.nanobaseai.actenora.aiprocessing.domain.routing.CandidateEvaluation;
import com.nanobaseai.actenora.aiprocessing.domain.routing.FallbackStep;
import com.nanobaseai.actenora.aiprocessing.domain.routing.InferenceTaskType;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ModelChangeProvenance;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ModelRole;
import com.nanobaseai.actenora.aiprocessing.domain.routing.RoutingDecision;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcJson;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class JdbcRoutingDecisionStore implements RoutingDecisionStorePort {

    private static final RowMapper<RoutingDecision> DECISION_MAPPER = (rs, rowNum) -> {
        CandidateEvaluation[] candidates = Optional.ofNullable(
                        JdbcJson.read(rs.getString("candidates_json"), CandidateEvaluation[].class))
                .orElse(new CandidateEvaluation[0]);
        return new RoutingDecision(
                rs.getObject("id", UUID.class),
                rs.getObject("job_id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("correlation_id", UUID.class),
                InferenceTaskType.valueOf(rs.getString("task_type")),
                ModelRole.valueOf(rs.getString("requested_role")),
                FallbackStep.valueOf(rs.getString("fallback_step")),
                Optional.ofNullable(rs.getObject("selected_model_definition_id", UUID.class)),
                Optional.ofNullable(rs.getObject("selected_deployment_id", UUID.class)),
                Optional.ofNullable(rs.getString("selected_model_key")),
                rs.getBoolean("quality_downgraded"),
                rs.getBoolean("requires_retry_queue"),
                rs.getBoolean("requires_manual_review"),
                rs.getString("reason"),
                List.of(candidates),
                JdbcInstant.get(rs, "decided_at")
        );
    };

    private static final RowMapper<ModelChangeProvenance> PROVENANCE_MAPPER = (rs, rowNum) -> new ModelChangeProvenance(
            rs.getObject("id", UUID.class),
            rs.getObject("job_id", UUID.class),
            rs.getObject("routing_decision_id", UUID.class),
            Optional.ofNullable(rs.getObject("from_model_definition_id", UUID.class)),
            Optional.ofNullable(rs.getObject("from_deployment_id", UUID.class)),
            Optional.ofNullable(rs.getString("from_model_key")),
            Optional.ofNullable(rs.getObject("to_model_definition_id", UUID.class)),
            Optional.ofNullable(rs.getObject("to_deployment_id", UUID.class)),
            Optional.ofNullable(rs.getString("to_model_key")),
            FallbackStep.valueOf(rs.getString("fallback_step")),
            rs.getBoolean("quality_downgraded"),
            rs.getString("reason"),
            JdbcInstant.get(rs, "recorded_at")
    );

    private final JdbcTemplate jdbc;

    public JdbcRoutingDecisionStore(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public void save(RoutingDecision decision) {
        jdbc.update("""
                        INSERT INTO aiprocessing.routing_decisions (
                            id, job_id, tenant_id, correlation_id, task_type, requested_role, fallback_step,
                            selected_model_definition_id, selected_deployment_id, selected_model_key,
                            quality_downgraded, requires_retry_queue, requires_manual_review,
                            reason, candidates_json, decided_at
                        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        ON CONFLICT (id) DO UPDATE SET
                            job_id = EXCLUDED.job_id,
                            tenant_id = EXCLUDED.tenant_id,
                            correlation_id = EXCLUDED.correlation_id,
                            task_type = EXCLUDED.task_type,
                            requested_role = EXCLUDED.requested_role,
                            fallback_step = EXCLUDED.fallback_step,
                            selected_model_definition_id = EXCLUDED.selected_model_definition_id,
                            selected_deployment_id = EXCLUDED.selected_deployment_id,
                            selected_model_key = EXCLUDED.selected_model_key,
                            quality_downgraded = EXCLUDED.quality_downgraded,
                            requires_retry_queue = EXCLUDED.requires_retry_queue,
                            requires_manual_review = EXCLUDED.requires_manual_review,
                            reason = EXCLUDED.reason,
                            candidates_json = EXCLUDED.candidates_json,
                            decided_at = EXCLUDED.decided_at
                        """,
                decision.decisionId(),
                decision.jobId(),
                decision.tenantId(),
                decision.correlationId(),
                decision.taskType().name(),
                decision.requestedRole().name(),
                decision.fallbackStep().name(),
                decision.selectedModelDefinitionId().orElse(null),
                decision.selectedDeploymentId().orElse(null),
                decision.selectedModelKey().orElse(null),
                decision.qualityDowngraded(),
                decision.requiresRetryQueue(),
                decision.requiresManualReview(),
                decision.reason(),
                JdbcJson.write(decision.candidatesConsidered()),
                JdbcInstant.toTimestamp(decision.decidedAt())
        );
    }

    @Override
    public void saveProvenance(ModelChangeProvenance provenance) {
        jdbc.update("""
                        INSERT INTO aiprocessing.model_change_provenance (
                            id, job_id, routing_decision_id,
                            from_model_definition_id, from_deployment_id, from_model_key,
                            to_model_definition_id, to_deployment_id, to_model_key,
                            fallback_step, quality_downgraded, reason, recorded_at
                        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                        ON CONFLICT (id) DO NOTHING
                        """,
                provenance.provenanceId(),
                provenance.jobId(),
                provenance.routingDecisionId(),
                provenance.fromModelDefinitionId().orElse(null),
                provenance.fromDeploymentId().orElse(null),
                provenance.fromModelKey().orElse(null),
                provenance.toModelDefinitionId().orElse(null),
                provenance.toDeploymentId().orElse(null),
                provenance.toModelKey().orElse(null),
                provenance.fallbackStep().name(),
                provenance.qualityDowngraded(),
                provenance.reason(),
                JdbcInstant.toTimestamp(provenance.recordedAt())
        );
    }

    @Override
    public Optional<RoutingDecision> findById(UUID decisionId) {
        List<RoutingDecision> rows = jdbc.query(
                "SELECT * FROM aiprocessing.routing_decisions WHERE id = ?",
                DECISION_MAPPER,
                decisionId
        );
        return rows.stream().findFirst();
    }

    @Override
    public List<RoutingDecision> findByJobId(UUID jobId) {
        return jdbc.query(
                "SELECT * FROM aiprocessing.routing_decisions WHERE job_id = ? ORDER BY decided_at",
                DECISION_MAPPER,
                jobId
        );
    }

    @Override
    public List<ModelChangeProvenance> findProvenanceByJobId(UUID jobId) {
        return jdbc.query(
                "SELECT * FROM aiprocessing.model_change_provenance WHERE job_id = ? ORDER BY recorded_at",
                PROVENANCE_MAPPER,
                jobId
        );
    }
}
