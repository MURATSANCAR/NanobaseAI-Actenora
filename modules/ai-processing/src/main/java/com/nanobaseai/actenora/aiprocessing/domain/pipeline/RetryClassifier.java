package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import java.util.Objects;

/**
 * Same error fingerprint repeated → permanent failure; otherwise category policy applies.
 */
public final class RetryClassifier {

    public RetryDecision classify(PipelineException failure, String previousFingerprintOrNull) {
        Objects.requireNonNull(failure, "failure");
        if (previousFingerprintOrNull != null
                && previousFingerprintOrNull.equals(failure.fingerprint())) {
            return RetryDecision.PERMANENT_FAILURE;
        }
        if (failure.category().isRetryableOnce()) {
            return RetryDecision.RETRY;
        }
        return RetryDecision.PERMANENT_FAILURE;
    }
}
