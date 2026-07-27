package com.nanobaseai.actenora.aiprocessing.application.port;

import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;

/**
 * Optional side-effect hook when a job permanently fails ({@code DEAD}).
 */
@FunctionalInterface
public interface AiJobDeadNotifier {

    void onPermanentlyFailed(AiJob job);

    static AiJobDeadNotifier noop() {
        return job -> { };
    }
}
