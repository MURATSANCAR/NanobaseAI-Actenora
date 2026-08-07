package com.nanobaseai.actenora.transcript.application.port.out;

import com.nanobaseai.actenora.transcript.api.event.TranscriptIntegrationEvents;
import com.nanobaseai.actenora.transcript.domain.Transcript;

/**
 * Publishes transcript integration events via the owning schema outbox.
 * Never participates in a distributed / XA transaction with other BCs.
 */
public interface TranscriptEventPublisher {

    void publishIngested(Transcript transcript);

    void publishReady(Transcript transcript, int segmentCount);

    static TranscriptEventPublisher noop() {
        return new TranscriptEventPublisher() {
            @Override
            public void publishIngested(Transcript transcript) {
                // no-op
            }

            @Override
            public void publishReady(Transcript transcript, int segmentCount) {
                // no-op
            }
        };
    }

    /**
     * Convenience for tests that assert typed events without broker.
     */
    default void publish(TranscriptIntegrationEvents.TranscriptIngested ignored) {
        // default unused
    }
}
