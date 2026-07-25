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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mutable in-memory telemetry feed for tests and local Operations Center.
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

    public static InMemoryOpsTelemetryPort seededDemo(Instant now) {
        InMemoryOpsTelemetryPort port = new InMemoryOpsTelemetryPort();
        port.setMeetingCount(12);
        port.setTranscriptPendingAgeSeconds(120);
        port.setAiQueueDepth(3);
        port.setQueues(List.of(
                new QueueDepth("actenora.commands", 2, 1, 1),
                new QueueDepth("actenora.dlq", 0, 0, 0)
        ));
        port.setWorkers(List.of(
                new WorkerHealth("worker-1", "ai", false, 1, 4, now, "UP")
        ));
        port.setModelPool(List.of(
                new ModelPoolMember("gpt-local", "dep-1", "ACTIVE", true, false, now, 0)
        ));
        port.setCertificates(List.of(
                new CertificateRecord("teams-webhook", now.plus(Duration.ofDays(90)), "CN=teams")
        ));
        port.setTenantThroughput(List.of(
                new TenantThroughput(TenantId.random(), 5, 4, 4)
        ));
        port.addRetry(new RetryEntry(
                UUID.randomUUID(),
                "meeting.MeetingEnded.v1",
                TenantId.random(),
                2,
                now.plusSeconds(30),
                "TRANSIENT",
                "RETRY",
                UUID.randomUUID()
        ));
        return port;
    }

    public List<Object> snapshotForDebug() {
        List<Object> snap = new ArrayList<>();
        snap.add(meetingCount.get());
        snap.add(aiQueueDepth.get());
        return snap;
    }
}
