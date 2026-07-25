package com.nanobaseai.actenora.delivery.application.worker;

import com.nanobaseai.actenora.delivery.application.DeliveryDispatcherService;
import com.nanobaseai.actenora.delivery.application.port.DeliveryRequestRepository;
import com.nanobaseai.actenora.delivery.domain.DeliveryRequest;
import com.nanobaseai.actenora.delivery.domain.DeliveryStatus;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Separately deployable delivery worker loop.
 *
 * <p>Runs against the same module JAR from {@code services/delivery-worker} (or a Spring Boot
 * profile) without sharing API HTTP threads. See SERVICE-EXTRACTION-PLAYBOOK.
 */
public final class DeliveryWorker {

    public static final String SERVICE_NAME = "delivery-worker";

    private final DeliveryRequestRepository repository;
    private final DeliveryDispatcherService dispatcher;
    private final InstantClock clock;
    private final int batchSize;
    private volatile boolean draining;

    public DeliveryWorker(
            DeliveryRequestRepository repository,
            DeliveryDispatcherService dispatcher,
            InstantClock clock,
            int batchSize
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be >= 1");
        }
        this.batchSize = batchSize;
    }

    public void beginDrain() {
        draining = true;
    }

    public boolean isDraining() {
        return draining;
    }

    /**
     * Polls due queued deliveries and processes them. Returns per-request outcomes.
     */
    public List<DeliveryStatus> pollOnce() {
        if (draining) {
            return List.of();
        }
        List<DeliveryRequest> due = repository.findDue(clock.now(), batchSize);
        List<DeliveryStatus> outcomes = new ArrayList<>(due.size());
        for (DeliveryRequest request : due) {
            outcomes.add(dispatcher.processNext(request));
        }
        return outcomes;
    }
}
