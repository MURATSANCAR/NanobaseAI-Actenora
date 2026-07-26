package com.nanobaseai.actenora.modelmanagement.infrastructure.persistence;

import com.nanobaseai.actenora.modelmanagement.application.ModelDeploymentRepository;
import com.nanobaseai.actenora.modelmanagement.domain.DeploymentStatus;
import com.nanobaseai.actenora.modelmanagement.domain.ModelDeployment;
import com.nanobaseai.actenora.modelmanagement.domain.ModelRegistryException;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC model deployment store ({@code modelmanagement.model_deployment}). */
public final class JdbcModelDeploymentRepository implements ModelDeploymentRepository {

    private static final String COLUMNS = """
            id, model_definition_id, deployment_key, endpoint, node_name, zone,
            hardware_type, gpu_type, gpu_count, cpu_count, memory_gb, max_concurrency,
            status, last_heartbeat_at, version
            """;

    private static final RowMapper<ModelDeployment> ROW_MAPPER = (rs, rowNum) -> new ModelDeployment(
            rs.getObject("id", UUID.class),
            rs.getObject("model_definition_id", UUID.class),
            rs.getString("deployment_key"),
            rs.getString("endpoint"),
            rs.getString("node_name"),
            rs.getString("zone"),
            rs.getString("hardware_type"),
            rs.getString("gpu_type"),
            rs.getInt("gpu_count"),
            rs.getInt("cpu_count"),
            rs.getInt("memory_gb"),
            rs.getInt("max_concurrency"),
            DeploymentStatus.valueOf(rs.getString("status")),
            JdbcInstant.get(rs, "last_heartbeat_at"),
            rs.getLong("version")
    );

    private final JdbcTemplate jdbc;

    public JdbcModelDeploymentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public Optional<ModelDeployment> findByKey(String deploymentKey) {
        String sql = "SELECT " + COLUMNS + " FROM modelmanagement.model_deployment WHERE deployment_key = ?";
        return jdbc.query(sql, ROW_MAPPER, deploymentKey).stream().findFirst();
    }

    @Override
    public boolean existsByKey(String deploymentKey) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM modelmanagement.model_deployment WHERE deployment_key = ?",
                Integer.class,
                deploymentKey
        );
        return count != null && count > 0;
    }

    @Override
    public void save(ModelDeployment deployment) {
        if (deployment.version() == 0L) {
            insert(deployment);
            return;
        }
        long previousVersion = deployment.version() - 1L;
        String updateSql = """
                UPDATE modelmanagement.model_deployment SET
                    endpoint = ?, node_name = ?, zone = ?, hardware_type = ?, gpu_type = ?,
                    gpu_count = ?, cpu_count = ?, memory_gb = ?, max_concurrency = ?,
                    status = ?, last_heartbeat_at = ?, version = ?
                WHERE id = ? AND version = ?
                """;
        int updated = jdbc.update(updateSql,
                deployment.endpoint(),
                deployment.nodeName(),
                deployment.zone(),
                deployment.hardwareType(),
                deployment.gpuType(),
                deployment.gpuCount(),
                deployment.cpuCount(),
                deployment.memoryGb(),
                deployment.maxConcurrency(),
                deployment.status().name(),
                JdbcInstant.toTimestamp(deployment.lastHeartbeatAt()),
                deployment.version(),
                deployment.id(),
                previousVersion
        );
        if (updated != 1) {
            throw new ModelRegistryException(
                    "OPTIMISTIC_LOCK_CONFLICT",
                    "Deployment " + deployment.id() + " version conflict (expected " + previousVersion + ")"
            );
        }
    }

    @Override
    public List<ModelDeployment> findByModelDefinitionId(UUID modelDefinitionId) {
        String sql = "SELECT " + COLUMNS + " FROM modelmanagement.model_deployment WHERE model_definition_id = ?";
        return jdbc.query(sql, ROW_MAPPER, modelDefinitionId);
    }

    @Override
    public List<ModelDeployment> findAll() {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM modelmanagement.model_deployment ORDER BY deployment_key",
                ROW_MAPPER
        );
    }

    private void insert(ModelDeployment deployment) {
        String sql = """
                INSERT INTO modelmanagement.model_deployment (
                    id, model_definition_id, deployment_key, endpoint, node_name, zone,
                    hardware_type, gpu_type, gpu_count, cpu_count, memory_gb, max_concurrency,
                    status, last_heartbeat_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbc.update(sql,
                deployment.id(),
                deployment.modelDefinitionId(),
                deployment.deploymentKey(),
                deployment.endpoint(),
                deployment.nodeName(),
                deployment.zone(),
                deployment.hardwareType(),
                deployment.gpuType(),
                deployment.gpuCount(),
                deployment.cpuCount(),
                deployment.memoryGb(),
                deployment.maxConcurrency(),
                deployment.status().name(),
                JdbcInstant.toTimestamp(deployment.lastHeartbeatAt()),
                deployment.version()
        );
    }
}
