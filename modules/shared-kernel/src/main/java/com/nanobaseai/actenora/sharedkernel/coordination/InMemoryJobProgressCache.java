package com.nanobaseai.actenora.sharedkernel.coordination;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryJobProgressCache implements JobProgressCache {

    private final Map<UUID, Progress> byMeeting = new ConcurrentHashMap<>();

    @Override
    public void put(UUID meetingOccurrenceId, Progress progress) {
        Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        Objects.requireNonNull(progress, "progress");
        byMeeting.put(meetingOccurrenceId, progress);
    }

    @Override
    public Optional<Progress> get(UUID meetingOccurrenceId) {
        Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        return Optional.ofNullable(byMeeting.get(meetingOccurrenceId));
    }
}
