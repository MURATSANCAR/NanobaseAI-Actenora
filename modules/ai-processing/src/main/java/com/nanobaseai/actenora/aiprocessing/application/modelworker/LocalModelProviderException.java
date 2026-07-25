package com.nanobaseai.actenora.aiprocessing.application.modelworker;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

/**
 * Safe provider/worker failure. Message and code must not contain prompt or response bodies.
 */
public final class LocalModelProviderException extends ActenoraException {

    private final ProviderFailureCategory category;
    private final boolean retryable;

    public LocalModelProviderException(
            ProviderFailureCategory category,
            String message,
            boolean retryable
    ) {
        super(category.name(), message);
        this.category = category;
        this.retryable = retryable;
    }

    public LocalModelProviderException(
            ProviderFailureCategory category,
            String message,
            boolean retryable,
            Throwable cause
    ) {
        super(category.name(), message, cause);
        this.category = category;
        this.retryable = retryable;
    }

    public ProviderFailureCategory category() {
        return category;
    }

    public boolean retryable() {
        return retryable;
    }

    public static LocalModelProviderException of(
            ProviderFailureCategory category,
            String message,
            boolean retryable
    ) {
        return new LocalModelProviderException(category, message, retryable);
    }

    public static LocalModelProviderException of(
            ProviderFailureCategory category,
            String message,
            boolean retryable,
            Throwable cause
    ) {
        return new LocalModelProviderException(category, message, retryable, cause);
    }
}
