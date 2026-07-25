package com.nanobaseai.actenora.aiprocessing.infrastructure.routing;

import com.nanobaseai.actenora.aiprocessing.application.port.AttemptHistoryPort;
import com.nanobaseai.actenora.aiprocessing.domain.routing.AttemptHistory;
import com.nanobaseai.actenora.aiprocessing.domain.routing.AttemptRecord;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryAttemptHistoryStore implements AttemptHistoryPort {

    private final Map<UUID, AttemptHistory> byJob = new ConcurrentHashMap<>();
    private final Map<UUID, AttemptRecord> latestByAttemptId = new ConcurrentHashMap<>();

    @Override
    public AttemptHistory getOrCreate(UUID jobId) {
        return byJob.computeIfAbsent(jobId, AttemptHistory::new);
    }

    @Override
    public void append(AttemptRecord attempt) {
        AttemptHistory history = getOrCreate(attempt.jobId());
        history.append(attempt);
        latestByAttemptId.put(attempt.attemptId(), attempt);
    }

    @Override
    public void complete(AttemptRecord completed) {
        AttemptHistory history = byJob.get(completed.jobId());
        if (history == null) {
            throw new IllegalArgumentException("unknown job");
        }
        history.replace(completed);
        latestByAttemptId.put(completed.attemptId(), completed);
    }

    @Override
    public Optional<AttemptHistory> find(UUID jobId) {
        return Optional.ofNullable(byJob.get(jobId));
    }

    public Optional<AttemptRecord> findAttempt(UUID attemptId) {
        return Optional.ofNullable(latestByAttemptId.get(attemptId));
    }
}
