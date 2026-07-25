package com.nanobaseai.actenora.approval.domain.exception;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

/**
 * Raised when a dispute acceptance is misinterpreted as an in-place overwrite.
 * Corrections must produce a new draft version via the note lifecycle.
 */
public final class SilentOverwriteForbiddenException extends ActenoraException {

    public SilentOverwriteForbiddenException() {
        super(
                "SILENT_OVERWRITE_FORBIDDEN",
                "Participant correction must not silently overwrite the note version; create a new draft instead"
        );
    }
}
