package com.nanobaseai.actenora.aiprocessing.infrastructure.persistence;

import com.nanobaseai.actenora.aiprocessing.application.port.AiAttemptRepository;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiAttempt;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiAttemptStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryAiAttemptRepository implements AiAttemptRepository {

    private final Map<UUID, AiAttempt> store = new ConcurrentHashMap<>();

    @Override
    public void save(AiAttempt attempt) {
        store.put(attempt.id(), attempt);
    }

    @Override
    public Optional<AiAttempt> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<AiAttempt> findByJobId(UUID jobId) {
        return store.values().stream()
                .filter(a -> a.aiJobId().equals(jobId))
                .sorted((a, b) -> Integer.compare(a.attemptNumber(), b.attemptNumber()))
                .toList();
    }

    @Override
    public Optional<AiAttempt> findActiveByJobId(UUID jobId) {
        return store.values().stream()
                .filter(a -> a.aiJobId().equals(jobId))
                .filter(a -> a.status() == AiAttemptStatus.STARTED)
                .findFirst();
    }

    public void clear() {
        store.clear();
    }
}
