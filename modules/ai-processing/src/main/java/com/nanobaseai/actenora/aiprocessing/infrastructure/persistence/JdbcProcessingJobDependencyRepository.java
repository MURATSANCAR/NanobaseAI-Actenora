package com.nanobaseai.actenora.aiprocessing.infrastructure.persistence;

import com.nanobaseai.actenora.aiprocessing.application.port.ProcessingJobDependencyRepository;
import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingJobDependency;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class JdbcProcessingJobDependencyRepository implements ProcessingJobDependencyRepository {

    private static final RowMapper<ProcessingJobDependency> ROW_MAPPER = (rs, rowNum) -> new ProcessingJobDependency(
            rs.getObject("job_id", UUID.class),
            rs.getObject("depends_on_job_id", UUID.class),
            ProcessingJobDependency.Status.valueOf(rs.getString("status")),
            JdbcInstant.get(rs, "created_at")
    );

    private final JdbcTemplate jdbc;

    public JdbcProcessingJobDependencyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public void save(ProcessingJobDependency dependency) {
        jdbc.update(
                """
                        INSERT INTO aiprocessing.processing_job_dependency
                            (job_id, depends_on_job_id, status, created_at)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (job_id, depends_on_job_id) DO UPDATE SET status = EXCLUDED.status
                        """,
                dependency.jobId(),
                dependency.dependsOnJobId(),
                dependency.status().name(),
                JdbcInstant.toTimestamp(dependency.createdAt())
        );
    }

    @Override
    public void saveAll(List<ProcessingJobDependency> dependencies) {
        for (ProcessingJobDependency dependency : dependencies) {
            save(dependency);
        }
    }

    @Override
    public List<ProcessingJobDependency> findByJobId(UUID jobId) {
        return jdbc.query(
                """
                        SELECT job_id, depends_on_job_id, status, created_at
                        FROM aiprocessing.processing_job_dependency
                        WHERE job_id = ?
                        """,
                ROW_MAPPER,
                jobId
        );
    }

    @Override
    public List<ProcessingJobDependency> findByDependsOnJobId(UUID dependsOnJobId) {
        return jdbc.query(
                """
                        SELECT job_id, depends_on_job_id, status, created_at
                        FROM aiprocessing.processing_job_dependency
                        WHERE depends_on_job_id = ?
                        """,
                ROW_MAPPER,
                dependsOnJobId
        );
    }

    @Override
    public int countUnsatisfied(UUID jobId) {
        Integer count = jdbc.queryForObject(
                """
                        SELECT COUNT(*) FROM aiprocessing.processing_job_dependency
                        WHERE job_id = ? AND status = 'PENDING'
                        """,
                Integer.class,
                jobId
        );
        return count == null ? 0 : count;
    }

    @Override
    public void markSatisfiedForCompletedDependency(UUID completedJobId) {
        jdbc.update(
                """
                        UPDATE aiprocessing.processing_job_dependency
                        SET status = 'SATISFIED'
                        WHERE depends_on_job_id = ? AND status = 'PENDING'
                        """,
                completedJobId
        );
    }
}
