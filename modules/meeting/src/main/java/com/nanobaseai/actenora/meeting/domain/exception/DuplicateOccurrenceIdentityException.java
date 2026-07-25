package com.nanobaseai.actenora.meeting.domain.exception;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

public final class DuplicateOccurrenceIdentityException extends ActenoraException {

    public DuplicateOccurrenceIdentityException(String icalUid) {
        super(
                "DUPLICATE_OCCURRENCE_IDENTITY",
                "Meeting occurrence already exists for iCal identity: " + icalUid
        );
    }
}
