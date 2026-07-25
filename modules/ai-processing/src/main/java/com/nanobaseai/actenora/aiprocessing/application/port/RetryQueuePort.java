package com.nanobaseai.actenora.aiprocessing.application.port;

import com.nanobaseai.actenora.aiprocessing.domain.routing.RoutingDecision;

import java.util.List;
import java.util.UUID;

public interface RetryQueuePort {

    void enqueue(RoutingDecision decision);

    List<UUID> pendingJobIds();

    boolean remove(UUID jobId);
}
