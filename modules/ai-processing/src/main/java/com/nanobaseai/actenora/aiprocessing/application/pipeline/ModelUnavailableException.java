package com.nanobaseai.actenora.aiprocessing.application.pipeline;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

public final class ModelUnavailableException extends ActenoraException {

    public ModelUnavailableException(String message) {
        super("MODEL_UNAVAILABLE", message);
    }

    public ModelUnavailableException(String message, Throwable cause) {
        super("MODEL_UNAVAILABLE", message, cause);
    }
}
