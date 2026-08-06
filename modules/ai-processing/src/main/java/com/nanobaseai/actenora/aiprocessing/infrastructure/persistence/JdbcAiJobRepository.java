package com.nanobaseai.actenora.aiprocessing.infrastructure.persistence;

import com.nanobaseai.actenora.aiprocessing.application.port.AiJobRepository;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiCapability;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJobException;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJobStatus;
import com.nanobaseai.actenora.aiprocessing.domain.job.JobPriority;
import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingStage;
import com.nanobaseai.actenora.aiprocessing.domain.job.SelectedRoute;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcJson;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC AI job store ({@code aiprocessing.ai_jobs}). */
public class JdbcAiJobRepository implements AiJobRepository {

    private static final String COLUMNS = """
            id, tenant_id, meeting_occurrence_id, transcript_id, task_type, priority, status,
            requested_capability, selected_model_id, selected_deployment_id, selected_route_reason,
            selected_route_rejects, prompt_version, schema_version, input_token_count, output_token_count,
            queued_at, started_at, completed_at, deadline_at, next_eligible_at, correlation_id, language, context_size,
            fallback_permitted, admin_override_model_id, admin_override_deployment_id, attempt_count, version,
            parent_job_id, stage, idempotency_key, chunk_index, error_code, error_message
            """;

    private static final RowMapper<AiJob> ROW_MAPPER = (rs, rowNum) -> {
        SelectedRoute route = null;
        UUID selectedModelId = rs.getObject("selected_model_id", UUID.class);
        UUID selectedDeploymentId = rs.getObject("selected_deployment_id", UUID.class);
        String routeReason = rs.getString("selected_route_reason");
        if (selectedModelId != null && selectedDeploymentId != null && routeReason != null) {
            List<String> rejects = Optional.ofNullable(JdbcJson.read(rs.getString("selected_route_rejects"), RejectList.class))
                    .map(RejectList::rejects)
                    .orElse(List.of());
            route = new SelectedRoute(
                    selectedModelId,
                    selectedDeploymentId,
                    "unknown",
                    routeReason,
                    rejects,
                    JdbcInstant.get(rs, "queued_at")
            );
        }
        String stageRaw = rs.getString("stage");
        ProcessingStage stage = stageRaw == null || stageRaw.isBlank()
                ? ProcessingStage.fromTaskType(rs.getString("task_type"))
                : ProcessingStage.valueOf(stageRaw);
        return new AiJob(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("meeting_occurrence_id", UUID.class),
                rs.getObject("transcript_id", UUID.class),
                rs.getString("task_type"),
                JobPriority.valueOf(rs.getString("priority")),
                AiJobStatus.valueOf(rs.getString("status")),
                AiCapability.valueOf(rs.getString("requested_capability")),
                selectedModelId,
                selectedDeploymentId,
                route,
                rs.getString("prompt_version"),
                rs.getString("schema_version"),
                (Integer) rs.getObject("input_token_count"),
                (Integer) rs.getObject("output_token_count"),
                JdbcInstant.get(rs, "queued_at"),
                JdbcInstant.get(rs, "started_at"),
                JdbcInstant.get(rs, "completed_at"),
                JdbcInstant.get(rs, "deadline_at"),
                JdbcInstant.get(rs, "next_eligible_at"),
                rs.getObject("correlation_id", UUID.class),
                rs.getString("language"),
                rs.getInt("context_size"),
                rs.getBoolean("fallback_permitted"),
                rs.getObject("admin_override_model_id", UUID.class),
                rs.getObject("admin_override_deployment_id", UUID.class),
                rs.getLong("version"),
                rs.getInt("attempt_count"),
                rs.getObject("parent_job_id", UUID.class),
                stage,
                rs.getString("idempotency_key"),
                (Integer) rs.getObject("chunk_index"),
                rs.getString("error_code"),
                rs.getString("error_message")
        );
    };

    private final JdbcTemplate jdbc;

    public JdbcAiJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public void save(AiJob job) {
        if (findById(job.id()).isEmpty()) {
            insert(job);
            return;
        }
        long previousVersion = job.version() - 1L;
        String sql = """
                UPDATE aiprocessing.ai_jobs SET
                    priority = ?, status = ?, selected_model_id = ?, selected_deployment_id = ?,
                    selected_route_reason = ?, selected_route_rejects = ?, input_token_count = ?,
                    output_token_count = ?, started_at = ?, completed_at = ?, next_eligible_at = ?,
                    fallback_permitted = ?,
                    admin_override_model_id = ?, admin_override_deployment_id = ?, attempt_count = ?,
                    error_code = ?, error_message = ?,
                    version = ?
                WHERE id = ? AND version = ?
                """;
        SelectedRoute route = job.selectedRoute().orElse(null);
        int updated = jdbc.update(sql,
                job.priority().name(),
                job.status().name(),
                job.selectedModelId().orElse(null),
                job.selectedDeploymentId().orElse(null),
                route == null ? null : route.reason(),
                route == null ? null : JdbcJson.write(new RejectList(route.rejectReasons())),
                job.inputTokenCount().orElse(null),
                job.outputTokenCount().orElse(null),
                job.startedAt().map(JdbcInstant::toTimestamp).orElse(null),
                job.completedAt().map(JdbcInstant::toTimestamp).orElse(null),
                job.nextEligibleAt().map(JdbcInstant::toTimestamp).orElse(null),
                job.fallbackPermitted(),
                job.adminOverrideModelId().orElse(null),
                job.adminOverrideDeploymentId().orElse(null),
                job.attemptCount(),
                job.errorCode().orElse(null),
                job.errorMessage().orElse(null),
                job.version(),
                job.id(),
                previousVersion
        );
        if (updated != 1) {
            throw AiJobException.invalidTransition("Optimistic lock conflict for job " + job.id());
        }
    }

    @Override
    public Optional<AiJob> findById(UUID id) {
        String sql = "SELECT " + COLUMNS + " FROM aiprocessing.ai_jobs WHERE id = ?";
        return jdbc.query(sql, ROW_MAPPER, id).stream().findFirst();
    }

    @Override
    public Optional<AiJob> findByIdempotencyKey(UUID tenantId, String idempotencyKey) {
        String sql = "SELECT " + COLUMNS + " FROM aiprocessing.ai_jobs WHERE tenant_id = ? AND idempotency_key = ?";
        return jdbc.query(sql, ROW_MAPPER, tenantId, idempotencyKey).stream().findFirst();
    }

    @Override
    public Optional<AiJob> findDuplicate(
            UUID tenantId,
            UUID meetingOccurrenceId,
            UUID transcriptId,
            String taskType,
            UUID correlationId
    ) {
        String sql = "SELECT " + COLUMNS + """
                 FROM aiprocessing.ai_jobs
                 WHERE tenant_id = ? AND meeting_occurrence_id = ? AND transcript_id = ?
                   AND task_type = ? AND correlation_id = ?
                   AND status IN ('QUEUED', 'RUNNING')
                """;
        return jdbc.query(sql, ROW_MAPPER, tenantId, meetingOccurrenceId, transcriptId, taskType, correlationId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<AiJob> findLatestByTranscriptAndTaskType(
            UUID tenantId,
            UUID transcriptId,
            String taskType
    ) {
        String sql = "SELECT " + COLUMNS + """
                 FROM aiprocessing.ai_jobs
                 WHERE tenant_id = ? AND transcript_id = ? AND task_type = ?
                 ORDER BY queued_at DESC
                 LIMIT 1
                """;
        return jdbc.query(sql, ROW_MAPPER, tenantId, transcriptId, taskType).stream().findFirst();
    }

    @Override
    public Optional<AiJob> findActiveByMeetingAndCapability(
            UUID tenantId,
            UUID meetingOccurrenceId,
            com.nanobaseai.actenora.aiprocessing.domain.job.AiCapability capability
    ) {
        String sql = "SELECT " + COLUMNS + """
                 FROM aiprocessing.ai_jobs
                 WHERE tenant_id = ? AND meeting_occurrence_id = ? AND requested_capability = ?
                   AND status IN ('QUEUED', 'RUNNING')
                 ORDER BY queued_at ASC
                 LIMIT 1
                """;
        return jdbc.query(sql, ROW_MAPPER, tenantId, meetingOccurrenceId, capability.name())
                .stream().findFirst();
    }

    @Override
    public List<AiJob> findByStatus(AiJobStatus status) {
        String sql = "SELECT " + COLUMNS + " FROM aiprocessing.ai_jobs WHERE status = ?";
        return jdbc.query(sql, ROW_MAPPER, status.name());
    }

    @Override
    public List<AiJob> findByParentJobId(UUID parentJobId) {
        String sql = "SELECT " + COLUMNS + " FROM aiprocessing.ai_jobs WHERE parent_job_id = ? ORDER BY queued_at ASC";
        return jdbc.query(sql, ROW_MAPPER, parentJobId);
    }

    @Override
    public int countByTenantAndStatus(UUID tenantId, AiJobStatus status) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM aiprocessing.ai_jobs WHERE tenant_id = ? AND status = ?",
                Integer.class,
                tenantId,
                status.name()
        );
        return count == null ? 0 : count;
    }

    @Override
    public List<AiJob> findQueuedOrdered() {
        String sql = "SELECT " + COLUMNS + """
                 FROM aiprocessing.ai_jobs WHERE status = 'QUEUED' ORDER BY queued_at ASC
                """;
        return jdbc.query(sql, ROW_MAPPER);
    }

    @Override
    @Transactional
    public List<AiJob> lockEligibleQueued(Instant now, int limit) {
        return lockEligible(now, null, limit);
    }

    @Override
    @Transactional
    public List<AiJob> lockEligibleQueuedByStage(Instant now, ProcessingStage stage, int limit) {
        return lockEligible(now, stage, limit);
    }

    private List<AiJob> lockEligible(Instant now, ProcessingStage stage, int limit) {
        int capped = Math.max(1, Math.min(limit, 100));
        String qualified = """
                SELECT j.id, j.tenant_id, j.meeting_occurrence_id, j.transcript_id, j.task_type, j.priority, j.status,
                       j.requested_capability, j.selected_model_id, j.selected_deployment_id, j.selected_route_reason,
                       j.selected_route_rejects, j.prompt_version, j.schema_version, j.input_token_count, j.output_token_count,
                       j.queued_at, j.started_at, j.completed_at, j.deadline_at, j.next_eligible_at, j.correlation_id,
                       j.language, j.context_size, j.fallback_permitted, j.admin_override_model_id,
                       j.admin_override_deployment_id, j.attempt_count, j.version,
                       j.parent_job_id, j.stage, j.idempotency_key, j.chunk_index, j.error_code, j.error_message
                FROM aiprocessing.ai_jobs j
                WHERE j.status = 'QUEUED'
                  AND (j.next_eligible_at IS NULL OR j.next_eligible_at <= ?)
                """
                + (stage == null ? "" : " AND j.stage = ? ")
                + """
                  AND NOT EXISTS (
                      SELECT 1 FROM aiprocessing.processing_job_dependency d
                      WHERE d.job_id = j.id AND d.status = 'PENDING'
                  )
                ORDER BY j.queued_at ASC
                FOR UPDATE OF j SKIP LOCKED
                LIMIT ?
                """;
        if (stage == null) {
            return jdbc.query(qualified, ROW_MAPPER, JdbcInstant.toTimestamp(now), capped);
        }
        return jdbc.query(qualified, ROW_MAPPER, JdbcInstant.toTimestamp(now), stage.name(), capped);
    }

    @Override
    public List<AiJob> listByTenant(UUID tenantId) {
        String sql = "SELECT " + COLUMNS + """
                 FROM aiprocessing.ai_jobs WHERE tenant_id = ? ORDER BY queued_at DESC
                """;
        return jdbc.query(sql, ROW_MAPPER, tenantId);
    }

    private void insert(AiJob job) {
        String sql = """
                INSERT INTO aiprocessing.ai_jobs (
                    id, tenant_id, meeting_occurrence_id, transcript_id, task_type, priority, status,
                    requested_capability, selected_model_id, selected_deployment_id, selected_route_reason,
                    selected_route_rejects, prompt_version, schema_version, input_token_count, output_token_count,
                    queued_at, started_at, completed_at, deadline_at, next_eligible_at, correlation_id, language, context_size,
                    fallback_permitted, admin_override_model_id, admin_override_deployment_id, attempt_count, version,
                    parent_job_id, stage, idempotency_key, chunk_index, error_code, error_message
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        SelectedRoute route = job.selectedRoute().orElse(null);
        jdbc.update(sql,
                job.id(),
                job.tenantId(),
                job.meetingOccurrenceId(),
                job.transcriptId(),
                job.taskType(),
                job.priority().name(),
                job.status().name(),
                job.requestedCapability().name(),
                job.selectedModelId().orElse(null),
                job.selectedDeploymentId().orElse(null),
                route == null ? null : route.reason(),
                route == null ? null : JdbcJson.write(new RejectList(route.rejectReasons())),
                job.promptVersion(),
                job.schemaVersion(),
                job.inputTokenCount().orElse(null),
                job.outputTokenCount().orElse(null),
                JdbcInstant.toTimestamp(job.queuedAt()),
                job.startedAt().map(JdbcInstant::toTimestamp).orElse(null),
                job.completedAt().map(JdbcInstant::toTimestamp).orElse(null),
                JdbcInstant.toTimestamp(job.deadlineAt()),
                job.nextEligibleAt().map(JdbcInstant::toTimestamp).orElse(null),
                job.correlationId(),
                job.language(),
                job.contextSize(),
                job.fallbackPermitted(),
                job.adminOverrideModelId().orElse(null),
                job.adminOverrideDeploymentId().orElse(null),
                job.attemptCount(),
                job.version(),
                job.parentJobId().orElse(null),
                job.stage().name(),
                job.idempotencyKey(),
                job.chunkIndex().orElse(null),
                job.errorCode().orElse(null),
                job.errorMessage().orElse(null)
        );
    }

    private record RejectList(List<String> rejects) {
    }
}
