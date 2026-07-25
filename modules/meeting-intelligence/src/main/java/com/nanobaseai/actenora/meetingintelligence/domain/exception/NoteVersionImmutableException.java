package com.nanobaseai.actenora.meetingintelligence.domain.exception;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

public final class NoteVersionImmutableException extends ActenoraException {

    public NoteVersionImmutableException() {
        super(
                "NOTE_VERSION_IMMUTABLE",
                "Meeting note versions are immutable; create a new version instead"
        );
    }
}
