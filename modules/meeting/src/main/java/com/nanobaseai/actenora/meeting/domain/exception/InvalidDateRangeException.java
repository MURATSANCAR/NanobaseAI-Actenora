package com.nanobaseai.actenora.meeting.domain.exception;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

public final class InvalidDateRangeException extends ActenoraException {

    public InvalidDateRangeException(String message) {
        super("INVALID_DATE_RANGE", message);
    }
}
