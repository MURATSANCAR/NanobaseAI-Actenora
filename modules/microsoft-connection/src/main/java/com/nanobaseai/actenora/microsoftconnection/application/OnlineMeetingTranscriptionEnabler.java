package com.nanobaseai.actenora.microsoftconnection.application;

import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarEvent;
import com.nanobaseai.actenora.microsoftconnection.application.model.OnlineMeetingMetadata;
import com.nanobaseai.actenora.microsoftconnection.application.port.OnlineMeetingGateway;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.graph.GraphApiException;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Ensures Teams online meetings have transcription enabled via Graph before they start.
 * Failures are logged and skipped so calendar ingest is not blocked.
 */
public final class OnlineMeetingTranscriptionEnabler {

    private static final Logger log = LoggerFactory.getLogger(OnlineMeetingTranscriptionEnabler.class);

    private final OnlineMeetingGateway onlineMeetingGateway;
    private final InstantClock clock;
    private final boolean enabled;

    public OnlineMeetingTranscriptionEnabler(
            OnlineMeetingGateway onlineMeetingGateway,
            InstantClock clock,
            boolean enabled
    ) {
        this.onlineMeetingGateway = Objects.requireNonNull(onlineMeetingGateway, "onlineMeetingGateway");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.enabled = enabled;
    }

    public void enableForUpcomingMeetings(UUID tenantId, String userId, List<CalendarEvent> events) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        if (!enabled || events == null || events.isEmpty()) {
            return;
        }
        Instant now = clock.now();
        for (CalendarEvent event : events) {
            if (event.cancelled()) {
                continue;
            }
            if (event.endAt() != null && !event.endAt().isAfter(now)) {
                continue;
            }
            if (event.joinWebUrlOptional().isEmpty() && event.onlineMeetingIdOptional().isEmpty()) {
                continue;
            }
            try {
                resolveMeetingId(tenantId, userId, event).ifPresent(meetingId ->
                        onlineMeetingGateway.enableTranscription(tenantId, userId, meetingId));
            } catch (GraphApiException ex) {
                log.warn(
                        "Auto-enable transcription skipped for graphEvent={} code={}: {}",
                        event.immutableIdentity().graphEventImmutableId(),
                        ex.code(),
                        ex.getMessage());
            } catch (RuntimeException ex) {
                log.warn(
                        "Auto-enable transcription failed for graphEvent={}: {}",
                        event.immutableIdentity().graphEventImmutableId(),
                        ex.getMessage());
            }
        }
    }

    private Optional<String> resolveMeetingId(UUID tenantId, String userId, CalendarEvent event) {
        Optional<String> meetingId = event.onlineMeetingIdOptional();
        if (meetingId.isPresent()) {
            return meetingId;
        }
        return event.joinWebUrlOptional()
                .flatMap(url -> onlineMeetingGateway.getByJoinWebUrl(tenantId, userId, url))
                .map(OnlineMeetingMetadata::meetingId);
    }
}
