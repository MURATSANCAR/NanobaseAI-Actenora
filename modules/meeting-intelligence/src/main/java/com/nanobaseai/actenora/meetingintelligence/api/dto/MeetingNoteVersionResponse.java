package com.nanobaseai.actenora.meetingintelligence.api.dto;

import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNoteStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.model.NoteVersionSource;

import java.time.Instant;
import java.util.UUID;

public record MeetingNoteVersionResponse(
        UUID id,
        UUID noteId,
        int versionNumber,
        String executiveSummary,
        NoteVersionSource source,
        ProvenanceResponse provenance,
        String correctionReason,
        UUID createdByUserId,
        Instant createdAt,
        MeetingNoteStatus approvalStatus
) {
}
