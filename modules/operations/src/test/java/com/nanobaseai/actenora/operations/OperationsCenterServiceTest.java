package com.nanobaseai.actenora.operations;

import com.nanobaseai.actenora.observability.CorrelationIds;
import com.nanobaseai.actenora.observability.PipelineStage;
import com.nanobaseai.actenora.observability.PiiRedactor;
import com.nanobaseai.actenora.observability.StructuredLogEvent;
import com.nanobaseai.actenora.observability.otel.PipelineTracer;
import com.nanobaseai.actenora.operations.application.OperationsCenterService;
import com.nanobaseai.actenora.operations.application.OperationsViews;
import com.nanobaseai.actenora.operations.domain.AlertSeverity;
import com.nanobaseai.actenora.operations.domain.AlertThresholds;
import com.nanobaseai.actenora.operations.domain.AlertType;
import com.nanobaseai.actenora.operations.domain.CertificateRecord;
import com.nanobaseai.actenora.operations.domain.ModelPoolMember;
import com.nanobaseai.actenora.operations.domain.QueueDepth;
import com.nanobaseai.actenora.operations.domain.RetryEntry;
import com.nanobaseai.actenora.operations.domain.SlaObservation;
import com.nanobaseai.actenora.operations.domain.WorkerHealth;
import com.nanobaseai.actenora.operations.infrastructure.InMemoryOpsTelemetryPort;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.DeadLetterEvent;
import com.nanobaseai.actenora.sharedkernel.messaging.OutboxEvent;
import com.nanobaseai.actenora.sharedkernel.messaging.OutboxStatus;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryDeadLetterStore;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryInboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryOutboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.replay.EventReplayer;
import com.nanobaseai.actenora.sharedkernel.messaging.support.TenantFairnessTracker;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FAZ 25 Operations Center tests: dashboards, DLQ recovery, alerts, PII-safe logs, trace continuity.
 */
class OperationsCenterServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-25T18:00:00Z");

    private InMemoryOpsTelemetryPort telemetry;
    private InMemoryDeadLetterStore dlq;
    private InMemoryOutboxStore outbox;
    private OperationsCenterService service;

    @BeforeEach
    void setUp() {
        telemetry = new InMemoryOpsTelemetryPort();
        dlq = new InMemoryDeadLetterStore();
        outbox = new InMemoryOutboxStore(new TenantFairnessTracker());
        InMemoryInboxStore inbox = new InMemoryInboxStore();
        InstantClock clock = new InstantClock(Clock.fixed(NOW, ZoneOffset.UTC));
        EventReplayer replay = new EventReplayer(outbox, inbox, dlq, clock);
        service = new OperationsCenterService(
                telemetry,
                dlq,
                replay,
                AlertThresholds.defaults(),
                clock
        );
    }

    @Test
    void queueDashboardAndWorkerHealthVisible() {
        telemetry.setMeetingCount(7);
        telemetry.setTranscriptPendingAgeSeconds(45);
        telemetry.setAiQueueDepth(4);
        telemetry.setMailFailures(1);
        telemetry.setQueues(List.of(new QueueDepth("actenora.commands", 3, 1, 2)));
        telemetry.setWorkers(List.of(
                new WorkerHealth("w1", "ai", false, 1, 4, NOW, "UP"),
                new WorkerHealth("w2", "delivery", false, 0, 2, NOW.minusSeconds(120), "UP")
        ));

        OperationsViews.QueueDashboardView queues = service.queueDashboard();
        assertEquals(7, queues.meetingCount());
        assertEquals(45, queues.transcriptPendingAgeSeconds());
        assertEquals(4, queues.aiQueueDepth());
        assertEquals(1, queues.mailFailures());
        assertEquals(1, queues.queues().size());

        OperationsViews.WorkerHealthView workers = service.workerHealth();
        assertEquals(2, workers.workers().size());
        assertEquals(1, workers.healthyCount());
        assertEquals(1, workers.staleCount());
    }

    @Test
    void retryVisibility() {
        UUID eventId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        TenantId tenantId = TenantId.random();
        telemetry.setRetries(List.of(new RetryEntry(
                eventId,
                "meeting.MeetingEnded.v1",
                tenantId,
                3,
                NOW.plusSeconds(15),
                "TIMEOUT",
                "RETRY",
                correlationId
        )));

        OperationsViews.RetryViewerView view = service.retryViewer(10);
        assertEquals(1, view.retries().size());
        RetryEntry retry = view.retries().getFirst();
        assertEquals(eventId, retry.eventId());
        assertEquals(3, retry.attemptCount());
        assertEquals("TIMEOUT", retry.failureCodeOptional().orElseThrow());
        assertEquals(correlationId, retry.correlationId());
        assertEquals("RETRY", retry.status());
    }

    @Test
    void dlqViewerAndRecovery() {
        UUID eventId = UUID.randomUUID();
        TenantId tenantId = TenantId.random();
        UUID correlationId = UUID.randomUUID();
        OutboxEvent dead = new OutboxEvent(
                eventId,
                "Meeting",
                "m-1",
                tenantId,
                "meeting.MeetingEnded.v1",
                1,
                "{\"meetingId\":\"m-1\"}",
                correlationId,
                null,
                "trace-abc",
                NOW.minusSeconds(60),
                null,
                OutboxStatus.DEAD_LETTER,
                5,
                NOW.minusSeconds(10),
                "POISON"
        );
        outbox.append(dead);

        UUID dlqId = UUID.randomUUID();
        dlq.append(new DeadLetterEvent(
                dlqId,
                DeadLetterEvent.DeadLetterSource.OUTBOX,
                eventId,
                null,
                "meeting.MeetingEnded.v1",
                1,
                "{\"meetingId\":\"m-1\"}",
                "POISON",
                "schema reject",
                correlationId,
                tenantId,
                5,
                NOW.minusSeconds(5),
                null
        ));

        OperationsViews.DlqViewerView viewer = service.dlqViewer(10);
        assertEquals(1, viewer.items().size());
        assertFalse(viewer.items().getFirst().replayed());

        OperationsViews.ReprocessResultView dry = service.reprocessDeadLetter(
                dlqId, "ops-user", "investigate", true);
        assertTrue(dry.dryRun());
        assertFalse(dry.applied());
        assertEquals(OutboxStatus.DEAD_LETTER, outbox.findById(eventId).orElseThrow().status());

        OperationsViews.ReprocessResultView applied = service.reprocessDeadLetter(
                dlqId, "ops-user", "recover after fix", false);
        assertTrue(applied.applied());
        assertEquals(OutboxStatus.PENDING, outbox.findById(eventId).orElseThrow().status());
        assertTrue(dlq.findById(dlqId).orElseThrow().replayedAtOptional().isPresent());
    }

    @Test
    void modelPoolDashboard() {
        telemetry.setModelPool(List.of(
                new ModelPoolMember("m1", "d1", "ACTIVE", true, false, NOW, 1),
                new ModelPoolMember("m1", "d2", "UNHEALTHY", false, true, NOW.minusSeconds(90), 0)
        ));
        OperationsViews.ModelPoolDashboardView view = service.modelPoolDashboard();
        assertEquals(2, view.members().size());
        assertEquals(1, view.healthyCount());
        assertEquals(1, view.unhealthyCount());
    }

    @Test
    void certificateExpiryAndSlaBreachAlertThresholds() {
        telemetry.setCertificates(List.of(
                new CertificateRecord("edge-tls", NOW.plus(Duration.ofDays(5)), "CN=edge"),
                new CertificateRecord("ok-tls", NOW.plus(Duration.ofDays(120)), "CN=ok")
        ));
        telemetry.addSlaObservation(new SlaObservation(
                UUID.randomUUID(),
                TenantId.random(),
                NOW.minus(Duration.ofMinutes(90)),
                NOW,
                Duration.ofMinutes(60)
        ));
        telemetry.setAiQueueDepth(150);
        dlq.append(new DeadLetterEvent(
                UUID.randomUUID(),
                DeadLetterEvent.DeadLetterSource.TRANSPORT,
                UUID.randomUUID(),
                null,
                "x.y.v1",
                1,
                "{}",
                "FAIL",
                null,
                UUID.randomUUID(),
                TenantId.random(),
                1,
                NOW,
                null
        ));

        List<OperationsViews.AlertView> alerts = service.evaluateAlerts();
        assertTrue(alerts.stream().anyMatch(a ->
                a.type() == AlertType.CERTIFICATE_EXPIRY && a.severity() == AlertSeverity.WARNING));
        assertTrue(alerts.stream().anyMatch(a ->
                a.type() == AlertType.SLA_BREACH && a.severity() == AlertSeverity.CRITICAL));
        assertTrue(alerts.stream().anyMatch(a ->
                a.type() == AlertType.DLQ_DEPTH && a.severity() == AlertSeverity.WARNING));
        assertTrue(alerts.stream().anyMatch(a ->
                a.type() == AlertType.QUEUE_DEPTH));
        assertFalse(alerts.stream().anyMatch(a -> a.title().contains("ok-tls")));
    }

    @Test
    void alertThresholdsRespectCriticalDlq() {
        AlertThresholds tight = new AlertThresholds(
                Duration.ofDays(14),
                Duration.ofMinutes(30),
                1,
                2,
                50,
                300
        );
        InstantClock clock = new InstantClock(Clock.fixed(NOW, ZoneOffset.UTC));
        OperationsCenterService tightService = new OperationsCenterService(
                telemetry,
                dlq,
                new EventReplayer(outbox, new InMemoryInboxStore(), dlq, clock),
                tight,
                clock
        );
        for (int i = 0; i < 2; i++) {
            dlq.append(new DeadLetterEvent(
                    UUID.randomUUID(),
                    DeadLetterEvent.DeadLetterSource.OUTBOX,
                    UUID.randomUUID(),
                    null,
                    "e.v1",
                    1,
                    "{}",
                    "X",
                    null,
                    null,
                    null,
                    1,
                    NOW,
                    null
            ));
        }
        List<OperationsViews.AlertView> alerts = tightService.evaluateAlerts();
        assertTrue(alerts.stream().anyMatch(a ->
                a.type() == AlertType.DLQ_DEPTH && a.severity() == AlertSeverity.CRITICAL));
    }

    @Test
    void secretRedactionAndTranscriptAbsenceInOpsLogs() {
        String transcript = "Board discussed Q3 acquisition of Contoso";
        String log = StructuredLogEvent.info("operations", "dlq_view")
                .withCorrelation("corr-1", "evt-1", "job-1", "model-1", "dep-1")
                .withField("transcript", transcript)
                .withField("apiKey", "sk-super-secret")
                .withField("dlqId", "dlq-9")
                .toJson();
        assertFalse(log.contains(transcript));
        assertFalse(log.contains("sk-super-secret"));
        assertTrue(log.contains(PiiRedactor.REDACTED));
        assertTrue(log.contains("dlq-9"));
        assertTrue(log.contains("corr-1"));
    }

    @Test
    void traceContinuityMeetingDiscoveredToDelivered() {
        PipelineTracer tracer = PipelineTracer.start(
                CorrelationIds.of("corr-ops")
                        .withJobId("job-ops")
                        .withDeploymentId("dep-ops")
        );
        Instant t = NOW;
        for (PipelineStage stage : PipelineStage.values()) {
            tracer.recordStage(stage, t, t.plusMillis(50));
            t = t.plusSeconds(1);
        }
        assertTrue(tracer.isComplete());
        assertTrue(tracer.hasContinuousTrace());
        assertEquals(11, tracer.spans().size());
        assertEquals("MeetingDiscovered", tracer.stageNamesInOrder().getFirst());
        assertEquals("Delivered", tracer.stageNamesInOrder().getLast());
    }
}
