package com.nanobaseai.actenora.sharedkernel.coordination;

import java.util.Optional;
import java.util.UUID;

/** No-op progress cache for unit tests / disabled coordination. */
public final class NoOpJobProgressCache implements JobProgressCache {

    @Override
    public void put(UUID meetingOccurrenceId, Progress progress) {
        // intentionally empty
    }

    @Override
    public Optional<Progress> get(UUID meetingOccurrenceId) {
        return Optional.empty();
    }
}
