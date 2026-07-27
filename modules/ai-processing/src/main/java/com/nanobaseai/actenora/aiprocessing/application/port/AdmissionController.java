package com.nanobaseai.actenora.aiprocessing.application.port;

import com.nanobaseai.actenora.aiprocessing.domain.job.AiCapability;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.JobPriority;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Admission control for new AI jobs (capacity, duplicates, SLA feasibility).
 */
public interface AdmissionController {

    AdmissionDecision admit(SubmitAiJobCommand command);

    record SubmitAiJobCommand(
            UUID tenantId,
            UUID meetingOccurrenceId,
            UUID transcriptId,
            String taskType,
            JobPriority priority,
            AiCapability requestedCapability,
            String promptVersion,
            String schemaVersion,
            String language,
            int contextSize,
            Boolean fallbackPermittedOverride,
            UUID correlationId,
            Instant now,
            boolean forceReprocess
    ) {
        public SubmitAiJobCommand {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
            Objects.requireNonNull(transcriptId, "transcriptId");
            Objects.requireNonNull(taskType, "taskType");
            Objects.requireNonNull(priority, "priority");
            Objects.requireNonNull(requestedCapability, "requestedCapability");
            Objects.requireNonNull(promptVersion, "promptVersion");
            Objects.requireNonNull(schemaVersion, "schemaVersion");
            Objects.requireNonNull(correlationId, "correlationId");
            Objects.requireNonNull(now, "now");
        }

        /** Backward-compatible constructor (forceReprocess=false). */
        public SubmitAiJobCommand(
                UUID tenantId,
                UUID meetingOccurrenceId,
                UUID transcriptId,
                String taskType,
                JobPriority priority,
                AiCapability requestedCapability,
                String promptVersion,
                String schemaVersion,
                String language,
                int contextSize,
                Boolean fallbackPermittedOverride,
                UUID correlationId,
                Instant now
        ) {
            this(
                    tenantId, meetingOccurrenceId, transcriptId, taskType, priority, requestedCapability,
                    promptVersion, schemaVersion, language, contextSize, fallbackPermittedOverride,
                    correlationId, now, false
            );
        }
    }

    record AdmissionDecision(
            boolean admitted,
            AiJob job,
            Duration estimatedQueueWait,
            String rejectReason
    ) {
        public static AdmissionDecision accepted(AiJob job, Duration estimatedQueueWait) {
            return new AdmissionDecision(true, job, estimatedQueueWait, null);
        }

        public static AdmissionDecision rejected(String reason) {
            return new AdmissionDecision(false, null, Duration.ZERO, reason);
        }
    }
}
