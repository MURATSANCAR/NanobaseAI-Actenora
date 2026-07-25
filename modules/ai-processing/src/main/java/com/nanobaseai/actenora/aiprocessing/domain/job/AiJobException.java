package com.nanobaseai.actenora.aiprocessing.domain.job;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

/**
 * Domain/application errors for AI job admission, routing, and lifecycle.
 */
public final class AiJobException extends ActenoraException {

    public AiJobException(String code, String message) {
        super(code, message);
    }

    public static AiJobException duplicate(String detail) {
        return new AiJobException("AI_JOB_DUPLICATE", detail);
    }

    public static AiJobException capacityExhausted(String detail) {
        return new AiJobException("AI_JOB_CAPACITY_EXHAUSTED", detail);
    }

    public static AiJobException routingFailed(String detail) {
        return new AiJobException("AI_JOB_ROUTING_FAILED", detail);
    }

    public static AiJobException notFound(String detail) {
        return new AiJobException("AI_JOB_NOT_FOUND", detail);
    }

    public static AiJobException invalidTransition(String detail) {
        return new AiJobException("AI_JOB_INVALID_TRANSITION", detail);
    }

    public static AiJobException forbidden(String detail) {
        return new AiJobException("AI_JOB_FORBIDDEN", detail);
    }

    public static AiJobException admissionRejected(String detail) {
        return new AiJobException("AI_JOB_ADMISSION_REJECTED", detail);
    }
}
