package com.nanobaseai.actenora.aiprocessing.infrastructure.persistence;

import com.nanobaseai.actenora.aiprocessing.application.port.AiJobRepository;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiCapability;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJobException;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJobStatus;
import com.nanobaseai.actenora.aiprocessing.domain.job.JobPriority;
import com.nanobaseai.actenora.aiprocessing.domain.job.SelectedRoute;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcJson;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC AI job store ({@code aiprocessing.ai_jobs}). */
public final class JdbcAiJobRepository implements AiJobRepository {

    private static final String COLUMNS = """
            id, tenant_id, meeting_occurrence_id, transcript_id, task_type, priority, status,
            requested_capability, selected_model_id, selected_deployment_id, selected_route_reason,
            selected_route_rejects, prompt_version, schema_version, input_token_count, output_token_count,
            queued_at, started_at, completed_at, deadline_at, correlation_id, language, context_size,
            fallback_permitted, admin_override_model_id, admin_override_deployment_id, attempt_count, version
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
                rs.getObject("correlation_id", UUID.class),
                rs.getString("language"),
                rs.getInt("context_size"),
                rs.getBoolean("fallback_permitted"),
                rs.getObject("admin_override_model_id", UUID.class),
                rs.getObject("admin_override_deployment_id", UUID.class),
                rs.getLong("version"),
                rs.getInt("attempt_count")
        );
    };

    private final JdbcTemplate jdbc;

    public JdbcAiJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public void save(AiJob job) {
        if (job.version() == 0L) {
            insert(job);
            return;
        }
        long previousVersion = job.version() - 1L;
        String sql = """
                UPDATE aiprocessing.ai_jobs SET
                    priority = ?, status = ?, selected_model_id = ?, selected_deployment_id = ?,
                    selected_route_reason = ?, selected_route_rejects = ?, input_token_count = ?,
                    output_token_count = ?, started_at = ?, completed_at = ?, fallback_permitted = ?,
                    admin_override_model_id = ?, admin_override_deployment_id = ?, attempt_count = ?,
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
                job.fallbackPermitted(),
                job.adminOverrideModelId().orElse(null),
                job.adminOverrideDeploymentId().orElse(null),
                job.attemptCount(),
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
    public List<AiJob> findByStatus(AiJobStatus status) {
        String sql = "SELECT " + COLUMNS + " FROM aiprocessing.ai_jobs WHERE status = ?";
        return jdbc.query(sql, ROW_MAPPER, status.name());
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
                    queued_at, started_at, completed_at, deadline_at, correlation_id, language, context_size,
                    fallback_permitted, admin_override_model_id, admin_override_deployment_id, attempt_count, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                job.correlationId(),
                job.language(),
                job.contextSize(),
                job.fallbackPermitted(),
                job.adminOverrideModelId().orElse(null),
                job.adminOverrideDeploymentId().orElse(null),
                job.attemptCount(),
                job.version()
        );
    }

    private record RejectList(List<String> rejects) {
    }
}
