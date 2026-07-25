package com.nanobaseai.actenora.aiprocessing.infrastructure.routing;

import com.nanobaseai.actenora.aiprocessing.application.port.RetryQueuePort;
import com.nanobaseai.actenora.aiprocessing.domain.routing.RoutingDecision;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InMemoryRetryQueue implements RetryQueuePort {

    private final Map<UUID, RoutingDecision> pending = new LinkedHashMap<>();

    @Override
    public synchronized void enqueue(RoutingDecision decision) {
        pending.put(decision.jobId(), decision);
    }

    @Override
    public synchronized List<UUID> pendingJobIds() {
        return List.copyOf(new ArrayList<>(pending.keySet()));
    }

    @Override
    public synchronized boolean remove(UUID jobId) {
        return pending.remove(jobId) != null;
    }
}
