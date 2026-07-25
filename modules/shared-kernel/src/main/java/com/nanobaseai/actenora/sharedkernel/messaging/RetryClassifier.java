package com.nanobaseai.actenora.sharedkernel.messaging;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

/**
 * Maps failures to {@link RetryClassification}.
 */
public interface RetryClassifier {

    RetryClassification classify(Throwable error);

    RetryClassification classify(String failureCode);

    final class Default implements RetryClassifier {

        public static final String CODE_TRANSIENT = "TRANSIENT";
        public static final String CODE_POISON = "POISON";
        public static final String CODE_MALFORMED = "MALFORMED_PAYLOAD";
        public static final String CODE_UNSUPPORTED_VERSION = "UNSUPPORTED_VERSION";
        public static final String CODE_PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE";
        public static final String CODE_MAX_ATTEMPTS = "MAX_ATTEMPTS_EXCEEDED";

        @Override
        public RetryClassification classify(Throwable error) {
            if (error instanceof ActenoraException ae) {
                return classify(ae.code());
            }
            if (error instanceof IllegalArgumentException) {
                return RetryClassification.REJECT;
            }
            return RetryClassification.TRANSIENT;
        }

        @Override
        public RetryClassification classify(String failureCode) {
            if (failureCode == null) {
                return RetryClassification.TRANSIENT;
            }
            return switch (failureCode) {
                case CODE_MALFORMED, CODE_UNSUPPORTED_VERSION, CODE_PAYLOAD_TOO_LARGE ->
                        RetryClassification.REJECT;
                case CODE_POISON, CODE_MAX_ATTEMPTS -> RetryClassification.POISON;
                default -> RetryClassification.TRANSIENT;
            };
        }
    }
}
