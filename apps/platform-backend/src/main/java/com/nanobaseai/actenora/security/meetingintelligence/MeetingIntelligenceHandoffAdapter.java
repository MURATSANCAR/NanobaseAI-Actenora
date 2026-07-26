package com.nanobaseai.actenora.security.meetingintelligence;

import com.nanobaseai.actenora.aiprocessing.application.port.MeetingNoteHandoffPort;
import com.nanobaseai.actenora.aiprocessing.application.port.TranscriptSegmentSourcePort;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.meetingintelligence.api.EvidenceValidationApi;
import com.nanobaseai.actenora.meetingintelligence.api.MeetingIntelligenceApi;
import com.nanobaseai.actenora.meetingintelligence.api.RunValidationCommand;
import com.nanobaseai.actenora.meetingintelligence.api.ValidationExecutionResult;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MapAiCandidatesCommand;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteDetailResponse;
import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingIntelligenceAuditPort;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.QualityGateOutcome;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Platform adapter: quality-gate FinalNoteDraft, then map into Meeting Intelligence when allowed.
 *
 * <p>PASSED / PASSED_WITH_WARNINGS → {@code mapAiCandidates}.
 * MANUAL_REVIEW_REQUIRED / REJECTED → no note; ManualReviewCase already opened by the validation service.
 */
public final class MeetingIntelligenceHandoffAdapter implements MeetingNoteHandoffPort {

    private final MeetingIntelligenceApi meetingIntelligenceApi;
    private final EvidenceValidationApi evidenceValidationApi;
    private final TranscriptSegmentSourcePort segmentSource;
    private final MeetingIntelligenceAuditPort auditPort;

    public MeetingIntelligenceHandoffAdapter(
            MeetingIntelligenceApi meetingIntelligenceApi,
            EvidenceValidationApi evidenceValidationApi,
            TranscriptSegmentSourcePort segmentSource,
            MeetingIntelligenceAuditPort auditPort
    ) {
        this.meetingIntelligenceApi = Objects.requireNonNull(meetingIntelligenceApi, "meetingIntelligenceApi");
        this.evidenceValidationApi = Objects.requireNonNull(evidenceValidationApi, "evidenceValidationApi");
        this.segmentSource = Objects.requireNonNull(segmentSource, "segmentSource");
        this.auditPort = Objects.requireNonNull(auditPort, "auditPort");
    }

    @Override
    public Optional<UUID> handoff(HandoffCommand command) {
        Objects.requireNonNull(command, "command");

        ValidationExecutionResult validation = runValidation(command);
        QualityGateOutcome outcome = validation.decision().outcome();
        auditGate(command, validation);
        if (outcome != QualityGateOutcome.PASSED && outcome != QualityGateOutcome.PASSED_WITH_WARNINGS) {
            return Optional.empty();
        }

        MeetingNoteDetailResponse note = meetingIntelligenceApi.mapAiCandidates(new MapAiCandidatesCommand(
                command.tenantId(),
                command.meetingOccurrenceId(),
                FinalNoteDraftMapper.toBundle(command.draft()),
                command.modelId(),
                command.promptVersionId(),
                command.schemaId(),
                clamp(command.draft().confidence())
        ));
        auditPort.record(
                command.tenantId(),
                "system:ai-handoff",
                "MEETING_NOTE_MAPPED_FROM_AI",
                "MeetingNote",
                note.id(),
                Map.of(
                        "jobId", command.jobId().toString(),
                        "meetingOccurrenceId", command.meetingOccurrenceId().toString(),
                        "promptVersionId", command.promptVersionId(),
                        "modelId", command.modelId(),
                        "decisionCount", note.decisions().size(),
                        "actionItemCount", note.actionItems().size(),
                        "qualityGateOutcome", outcome.name()
                ),
                Instant.now()
        );
        return Optional.of(note.id());
    }

    private ValidationExecutionResult runValidation(HandoffCommand command) {
        List<SegmentInput> segments = segmentSource.segmentsFor(
                TenantId.of(command.tenantId()), command.transcriptId());
        return evidenceValidationApi.validate(new RunValidationCommand(
                command.tenantId(),
                command.meetingOccurrenceId(),
                command.jobId(),
                ValidationCandidateMapper.toCandidates(command.draft()),
                ValidationCandidateMapper.toSegments(segments),
                ValidationCandidateMapper.participantsFromSpeakers(segments)
        ));
    }

    private void auditGate(HandoffCommand command, ValidationExecutionResult validation) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("jobId", command.jobId().toString());
        metadata.put("validationRunId", validation.run().id().toString());
        metadata.put("decisionId", validation.decision().id().toString());
        metadata.put("outcome", validation.decision().outcome().name());
        metadata.put("manualReview", validation.manualReviewCase().isPresent());
        auditPort.record(
                command.tenantId(),
                "system:ai-handoff",
                "QUALITY_GATE_" + validation.decision().outcome().name(),
                "QualityGateDecision",
                validation.decision().id(),
                metadata,
                Instant.now()
        );
    }

    private static double clamp(double confidence) {
        if (confidence < 0.0d) {
            return 0.0d;
        }
        if (confidence > 1.0d) {
            return 1.0d;
        }
        return confidence;
    }
}
