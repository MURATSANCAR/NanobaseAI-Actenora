package com.nanobaseai.actenora.security.operations;

import com.nanobaseai.actenora.microsoftconnection.infrastructure.config.MicrosoftGraphSpringProperties;
import com.nanobaseai.actenora.operations.application.port.OpsTelemetryPort;
import com.nanobaseai.actenora.operations.domain.WorkerHealth;
import com.nanobaseai.actenora.operations.infrastructure.InMemoryOpsTelemetryPort;
import com.nanobaseai.actenora.security.aiprocessing.NanobaseAiConnectionService;
import com.nanobaseai.actenora.security.microsoftconnection.TeamsTranscriptPollScheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Publishes platform worker heartbeats into the Operations Center telemetry feed.
 */
@Component
@ConditionalOnBean(OpsTelemetryPort.class)
public final class PlatformOpsWorkerTelemetryReporter {

    private final OpsTelemetryPort telemetry;
    private final ObjectProvider<TeamsTranscriptPollScheduler> transcriptPollScheduler;
    private final ObjectProvider<NanobaseAiConnectionService> intelligenceConnection;
    private final MicrosoftGraphSpringProperties graphProperties;
    private final boolean aiWorkerEnabled;
    private final boolean mailboxSyncEnabled;

    public PlatformOpsWorkerTelemetryReporter(
            OpsTelemetryPort telemetry,
            ObjectProvider<TeamsTranscriptPollScheduler> transcriptPollScheduler,
            ObjectProvider<NanobaseAiConnectionService> intelligenceConnection,
            MicrosoftGraphSpringProperties graphProperties,
            @Value("${actenora.ai.worker.enabled:true}") boolean aiWorkerEnabled,
            @Value("${actenora.microsoft-graph.mailbox-sync-enabled:true}") boolean mailboxSyncEnabled
    ) {
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        this.transcriptPollScheduler = transcriptPollScheduler;
        this.intelligenceConnection = intelligenceConnection;
        this.graphProperties = graphProperties;
        this.aiWorkerEnabled = aiWorkerEnabled;
        this.mailboxSyncEnabled = mailboxSyncEnabled;
    }

    @Scheduled(fixedDelayString = "${actenora.operations.worker-telemetry-interval:PT30S}", initialDelayString = "PT5S")
    void publishWorkerHeartbeats() {
        if (!(telemetry instanceof InMemoryOpsTelemetryPort inMemory)) {
            return;
        }
        Instant now = Instant.now();
        List<WorkerHealth> workers = new ArrayList<>();

        if (graphProperties.isEnabled() && graphProperties.isWorkersEnabled()) {
            int transcriptPending = transcriptPollScheduler.getIfAvailable() == null
                    ? 0
                    : (int) transcriptPollScheduler.getObject().pendingCount();
            workers.add(worker("graph-mailbox-sync", "microsoft-graph", 0, 1, now,
                    mailboxSyncEnabled ? "UP" : "DISABLED"));
            workers.add(worker("teams-transcript-poll", "microsoft-graph", transcriptPending, 1, now, "UP"));
            workers.add(worker("graph-reconciliation", "microsoft-graph", 0, 1, now, "UP"));
        }

        workers.add(worker(
                "ai-inference",
                "ai-processing",
                0,
                aiWorkerEnabled ? 4 : 0,
                now,
                aiWorkerEnabled ? "UP" : "DISABLED"
        ));

        NanobaseAiConnectionService intelligence = intelligenceConnection.getIfAvailable();
        if (intelligence != null) {
            NanobaseAiConnectionService.ConnectionView connection = intelligence.current();
            workers.add(worker(
                    "nanobaseai-intelligence",
                    "ai-processing",
                    0,
                    1,
                    now,
                    connection.enabled()
                            ? (connection.healthy() ? "UP" : "DEGRADED")
                            : "DISABLED"
            ));
        }

        inMemory.setWorkers(workers);
    }

    private static WorkerHealth worker(
            String workerId,
            String role,
            int inFlight,
            int maxConcurrency,
            Instant heartbeatAt,
            String status
    ) {
        return new WorkerHealth(workerId, role, false, inFlight, maxConcurrency, heartbeatAt, status);
    }
}
