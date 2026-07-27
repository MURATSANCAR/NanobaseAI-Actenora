package com.nanobaseai.actenora.aiprocessing.application.port;

import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingJobDependency;

import java.util.List;
import java.util.UUID;

public interface ProcessingJobDependencyRepository {

    void save(ProcessingJobDependency dependency);

    void saveAll(List<ProcessingJobDependency> dependencies);

    List<ProcessingJobDependency> findByJobId(UUID jobId);

    List<ProcessingJobDependency> findByDependsOnJobId(UUID dependsOnJobId);

    int countUnsatisfied(UUID jobId);

    void markSatisfiedForCompletedDependency(UUID completedJobId);
}
