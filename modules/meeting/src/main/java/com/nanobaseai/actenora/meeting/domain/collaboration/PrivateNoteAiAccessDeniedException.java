package com.nanobaseai.actenora.meeting.domain.collaboration;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

import java.util.UUID;

public final class PrivateNoteAiAccessDeniedException extends ActenoraException {

    public PrivateNoteAiAccessDeniedException(UUID noteId) {
        super(
                "PRIVATE_NOTE_AI_ACCESS_DENIED",
                "AI may not use private note without explicit owner permission: " + noteId
        );
    }
}
