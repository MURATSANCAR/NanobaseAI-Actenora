package com.nanobaseai.actenora.aiprocessing.application.pipeline.staged;

import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingStage;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import com.nanobaseai.actenora.sharedkernel.messaging.OutboxEvent;
import com.nanobaseai.actenora.sharedkernel.messaging.port.OutboxStore;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Enqueues stage wake-up commands via transactional outbox (relay → RabbitMQ).
 */
public final class StageCommandPublisher {

    private final OutboxStore outbox;

    public StageCommandPublisher(OutboxStore outbox) {
        this.outbox = Objects.requireNonNull(outbox, "outbox");
    }

    public void publishWakeup(
            UUID tenantId,
            UUID jobId,
            UUID meetingOccurrenceId,
            UUID correlationId,
            ProcessingStage stage,
            Instant now
    ) {
        Objects.requireNonNull(stage, "stage");
        if (stage == ProcessingStage.ROOT || stage == ProcessingStage.LEGACY) {
            return;
        }
        String payload = """
                {"jobId":"%s","meetingOccurrenceId":"%s","stage":"%s"}
                """.formatted(jobId, meetingOccurrenceId, stage.name()).trim();
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(),
                AiPipelineEvents.eventType(stage),
                1,
                now,
                TenantId.of(tenantId),
                "AiJob",
                jobId.toString(),
                correlationId == null ? jobId : correlationId,
                null,
                null,
                "ai-processing",
                payload
        );
        outbox.append(OutboxEvent.pending(envelope, now));
    }
}
