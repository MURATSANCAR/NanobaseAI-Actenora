package com.nanobaseai.actenora.aiprocessing.application.port;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Composition-root seam: after a successful extraction run, persist corporate note objects.
 *
 * <p>Implemented in the platform (not inside AI Processing) so Modulith does not create an
 * {@code aiprocessing → meetingintelligence} dependency. FAZ 17 may gate the map behind the
 * evidence quality gate and return empty when the draft is rejected or held for manual review.
 */
public interface MeetingNoteHandoffPort {

    /**
     * Validates (when wired) then maps a final note draft into Meeting Intelligence corporate objects.
     *
     * @return created meeting note id when the gate allows mapping
     */
    Optional<UUID> handoff(HandoffCommand command);

    record HandoffCommand(
            UUID tenantId,
            UUID meetingOccurrenceId,
            UUID transcriptId,
            UUID jobId,
            String modelId,
            String promptVersionId,
            String schemaId,
            String meetingStartedAtIso,
            String meetingTimezone,
            FinalNoteDraft draft
    ) {
        public HandoffCommand {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
            Objects.requireNonNull(transcriptId, "transcriptId");
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(modelId, "modelId");
            Objects.requireNonNull(promptVersionId, "promptVersionId");
            Objects.requireNonNull(schemaId, "schemaId");
            Objects.requireNonNull(draft, "draft");
        }
    }

    static MeetingNoteHandoffPort noop() {
        return command -> Optional.empty();
    }
}
