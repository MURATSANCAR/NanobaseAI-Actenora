package com.nanobaseai.actenora.microsoftconnection.application.model;

import com.nanobaseai.actenora.microsoftconnection.domain.identity.ImmutableGraphEventIdentity;
import com.nanobaseai.actenora.microsoftconnection.domain.identity.SeriesOccurrenceKind;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Calendar event snapshot from Graph (join URL is metadata, never identity).
 */
public record CalendarEvent(
        ImmutableGraphEventIdentity immutableIdentity,
        String eventId,
        String seriesMasterId,
        String iCalUId,
        SeriesOccurrenceKind occurrenceKind,
        String subject,
        Instant startAt,
        Instant endAt,
        Instant originalStartAt,
        String joinWebUrl,
        String onlineMeetingId,
        boolean cancelled,
        List<ParticipantMetadata> attendees
) {

    public CalendarEvent {
        Objects.requireNonNull(immutableIdentity, "immutableIdentity");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurrenceKind, "occurrenceKind");
        Objects.requireNonNull(attendees, "attendees");
        attendees = List.copyOf(attendees);
    }

    public Optional<String> seriesMasterIdOptional() {
        return Optional.ofNullable(seriesMasterId).filter(s -> !s.isBlank());
    }

    public Optional<String> iCalUIdOptional() {
        return Optional.ofNullable(iCalUId).filter(s -> !s.isBlank());
    }

    public Optional<Instant> originalStartAtOptional() {
        return Optional.ofNullable(originalStartAt);
    }

    public Optional<String> joinWebUrlOptional() {
        return Optional.ofNullable(joinWebUrl).filter(s -> !s.isBlank());
    }

    public Optional<String> onlineMeetingIdOptional() {
        return Optional.ofNullable(onlineMeetingId).filter(s -> !s.isBlank());
    }
}
