package com.nanobaseai.actenora.meeting.domain.exception;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

public final class DuplicateGraphIdentityException extends ActenoraException {

    public DuplicateGraphIdentityException(String graphEventImmutableId) {
        super(
                "DUPLICATE_GRAPH_IDENTITY",
                "Meeting already exists for Graph event identity: " + graphEventImmutableId
        );
    }
}
