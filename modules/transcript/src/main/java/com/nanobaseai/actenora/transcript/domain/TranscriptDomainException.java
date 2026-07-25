package com.nanobaseai.actenora.transcript.domain;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

public class TranscriptDomainException extends ActenoraException {

    public TranscriptDomainException(String code, String message) {
        super(code, message);
    }
}
