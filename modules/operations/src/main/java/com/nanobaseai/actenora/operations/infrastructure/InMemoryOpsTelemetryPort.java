package com.nanobaseai.actenora.operations.infrastructure;

import com.nanobaseai.actenora.operations.application.port.OpsTelemetryPort;
import com.nanobaseai.actenora.operations.domain.CertificateRecord;
import com.nanobaseai.actenora.operations.domain.ModelPoolMember;
import com.nanobaseai.actenora.operations.domain.QueueDepth;
import com.nanobaseai.actenora.operations.domain.RetryEntry;
import com.nanobaseai.actenora.operations.domain.SlaObservation;
import com.nanobaseai.actenora.operations.domain.TenantThroughput;
import com.nanobaseai.actenora.operations.domain.WorkerHealth;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mutable in-memory telemetry feed for tests and empty local Operations Center
 * (no canned demo metrics).
 */
public final class InMemoryOpsTelemetryPort implements OpsTelemetryPort {

    private final AtomicLong meetingCount = new AtomicLong();
    private final AtomicLong transcriptPendingAgeSeconds = new AtomicLong();
    private final AtomicLong aiQueueDepth = new AtomicLong();
    private final AtomicLong mailFailures = new AtomicLong();
    private final CopyOnWriteArrayList<QueueDepth> queues = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<WorkerHealth> workers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<RetryEntry> retries = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ModelPoolMember> modelPool = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<CertificateRecord> certificates = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<SlaObservation> slaObservations = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<TenantThroughput> tenantThroughput = new CopyOnWriteArrayList<>();

    @Override
    public long meetingCount() {
        return meetingCount.get();
    }

    @Override
    public long transcriptPendingAgeSeconds() {
        return transcriptPendingAgeSeconds.get();
    }

    @Override
    public long aiQueueDepth() {
        return aiQueueDepth.get();
    }

    @Override
    public long mailFailures() {
        return mailFailures.get();
    }

    @Override
    public List<QueueDepth> queueDepths() {
        return List.copyOf(queues);
    }

    @Override
    public List<WorkerHealth> workers() {
        return List.copyOf(workers);
    }

    @Override
    public List<RetryEntry> retries(int limit) {
        return retries.stream().limit(limit).toList();
    }

    @Override
    public List<ModelPoolMember> modelPool() {
        return List.copyOf(modelPool);
    }

    @Override
    public List<CertificateRecord> certificates() {
        return List.copyOf(certificates);
    }

    @Override
    public List<SlaObservation> recentSlaObservations(int limit) {
        return slaObservations.stream().limit(limit).toList();
    }

    @Override
    public List<TenantThroughput> tenantThroughput() {
        return List.copyOf(tenantThroughput);
    }

    public void setMeetingCount(long value) {
        meetingCount.set(value);
    }

    public void setTranscriptPendingAgeSeconds(long value) {
        transcriptPendingAgeSeconds.set(value);
    }

    public void setAiQueueDepth(long value) {
        aiQueueDepth.set(value);
    }

    public void setMailFailures(long value) {
        mailFailures.set(value);
    }

    public void setQueues(List<QueueDepth> values) {
        queues.clear();
        queues.addAll(values);
    }

    public void setWorkers(List<WorkerHealth> values) {
        workers.clear();
        workers.addAll(values);
    }

    public void addRetry(RetryEntry entry) {
        retries.add(entry);
    }

    public void setRetries(List<RetryEntry> values) {
        retries.clear();
        retries.addAll(values);
    }

    public void setModelPool(List<ModelPoolMember> values) {
        modelPool.clear();
        modelPool.addAll(values);
    }

    public void setCertificates(List<CertificateRecord> values) {
        certificates.clear();
        certificates.addAll(values);
    }

    public void addSlaObservation(SlaObservation observation) {
        slaObservations.add(observation);
    }

    public void setTenantThroughput(List<TenantThroughput> values) {
        tenantThroughput.clear();
        tenantThroughput.addAll(values);
    }

    public void clear() {
        meetingCount.set(0);
        transcriptPendingAgeSeconds.set(0);
        aiQueueDepth.set(0);
        mailFailures.set(0);
        queues.clear();
        workers.clear();
        retries.clear();
        modelPool.clear();
        certificates.clear();
        slaObservations.clear();
        tenantThroughput.clear();
    }

    public List<Object> snapshotForDebug() {
        List<Object> snap = new ArrayList<>();
        snap.add(meetingCount.get());
        snap.add(aiQueueDepth.get());
        return snap;
    }
}
