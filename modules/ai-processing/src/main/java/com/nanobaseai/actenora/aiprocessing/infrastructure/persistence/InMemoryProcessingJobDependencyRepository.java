package com.nanobaseai.actenora.aiprocessing.infrastructure.persistence;

import com.nanobaseai.actenora.aiprocessing.application.port.ProcessingJobDependencyRepository;
import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingJobDependency;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryProcessingJobDependencyRepository implements ProcessingJobDependencyRepository {

    private final Map<String, ProcessingJobDependency> store = new ConcurrentHashMap<>();

    private static String key(UUID jobId, UUID dependsOn) {
        return jobId + ":" + dependsOn;
    }

    @Override
    public void save(ProcessingJobDependency dependency) {
        store.put(key(dependency.jobId(), dependency.dependsOnJobId()), dependency);
    }

    @Override
    public void saveAll(List<ProcessingJobDependency> dependencies) {
        for (ProcessingJobDependency dependency : dependencies) {
            save(dependency);
        }
    }

    @Override
    public List<ProcessingJobDependency> findByJobId(UUID jobId) {
        List<ProcessingJobDependency> out = new ArrayList<>();
        for (ProcessingJobDependency dep : store.values()) {
            if (dep.jobId().equals(jobId)) {
                out.add(dep);
            }
        }
        return out;
    }

    @Override
    public List<ProcessingJobDependency> findByDependsOnJobId(UUID dependsOnJobId) {
        List<ProcessingJobDependency> out = new ArrayList<>();
        for (ProcessingJobDependency dep : store.values()) {
            if (dep.dependsOnJobId().equals(dependsOnJobId)) {
                out.add(dep);
            }
        }
        return out;
    }

    @Override
    public int countUnsatisfied(UUID jobId) {
        return (int) findByJobId(jobId).stream()
                .filter(d -> d.status() == ProcessingJobDependency.Status.PENDING)
                .count();
    }

    @Override
    public void markSatisfiedForCompletedDependency(UUID completedJobId) {
        for (ProcessingJobDependency dep : findByDependsOnJobId(completedJobId)) {
            if (dep.status() == ProcessingJobDependency.Status.PENDING) {
                dep.markSatisfied();
                save(dep);
            }
        }
    }

    public void clear() {
        store.clear();
    }
}
