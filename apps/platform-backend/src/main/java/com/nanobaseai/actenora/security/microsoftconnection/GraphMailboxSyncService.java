package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.microsoftconnection.api.MicrosoftConnectionApi;
import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarEvent;
import com.nanobaseai.actenora.microsoftconnection.application.port.CalendarSyncCursorStore;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pulls Graph calendar delta for a mailbox and upserts Actenora meetings.
 * Cursor advances only after each page is successfully upserted.
 *
 * <p>Delta pages often omit {@code attendees}; sparse events are enriched via
 * {@link MicrosoftConnectionApi#getCalendarEvent} before upsert so invitees sync on create/update.
 */
public final class GraphMailboxSyncService {

    private static final Logger log = LoggerFactory.getLogger(GraphMailboxSyncService.class);

    private final MicrosoftConnectionApi microsoftConnectionApi;
    private final CalendarMeetingUpsertAdapter calendarMeetingUpsertAdapter;
    private final CalendarSyncCursorStore calendarSyncCursorStore;

    public GraphMailboxSyncService(
            MicrosoftConnectionApi microsoftConnectionApi,
            CalendarMeetingUpsertAdapter calendarMeetingUpsertAdapter,
            CalendarSyncCursorStore calendarSyncCursorStore
    ) {
        this.microsoftConnectionApi = Objects.requireNonNull(microsoftConnectionApi);
        this.calendarMeetingUpsertAdapter = Objects.requireNonNull(calendarMeetingUpsertAdapter);
        this.calendarSyncCursorStore = Objects.requireNonNull(calendarSyncCursorStore);
    }

    public SyncResult syncMailbox(UUID tenantId, String mailboxUserId) {
        return syncMailbox(tenantId, mailboxUserId, false);
    }

    /**
     * When {@code recoverEmptyDelta} is true and delta sync returns no events while a cursor exists,
     * reset the cursor and retry once (covers missed webhooks / lost in-memory outbox after restart).
     */
    public SyncResult syncMailbox(UUID tenantId, String mailboxUserId, boolean recoverEmptyDelta) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(mailboxUserId, "mailboxUserId");
        SyncResult first = doSync(tenantId, mailboxUserId);
        if (!recoverEmptyDelta || first.eventsSynced() > 0) {
            return first;
        }
        if (calendarSyncCursorStore.find(tenantId, mailboxUserId).isEmpty()) {
            return first;
        }
        calendarSyncCursorStore.delete(tenantId, mailboxUserId);
        SyncResult recovered = doSync(tenantId, mailboxUserId);
        return new SyncResult(recovered.mailboxUserId(), recovered.eventsSynced(), true);
    }

    private SyncResult doSync(UUID tenantId, String mailboxUserId) {
        AtomicInteger eventsSynced = new AtomicInteger();
        microsoftConnectionApi.syncCalendar(tenantId, mailboxUserId, events -> {
            List<CalendarEvent> enriched = enrichSparseAttendees(tenantId, mailboxUserId, events);
            calendarMeetingUpsertAdapter.upsertEvents(TenantId.of(tenantId), enriched);
            microsoftConnectionApi.ensureTranscriptionForCalendarEvents(tenantId, mailboxUserId, enriched);
            eventsSynced.addAndGet(enriched.size());
        });
        return new SyncResult(mailboxUserId, eventsSynced.get(), false);
    }

    /**
     * Graph calendarView/delta frequently returns organizer-only (or empty) attendees.
     * Fetch the full event so invite roster + RSVP land in Actenora.
     */
    List<CalendarEvent> enrichSparseAttendees(UUID tenantId, String mailboxUserId, List<CalendarEvent> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        List<CalendarEvent> out = new ArrayList<>(events.size());
        for (CalendarEvent event : events) {
            if (event.cancelled() || !needsAttendeeEnrichment(event)) {
                out.add(event);
                continue;
            }
            try {
                CalendarEvent full = microsoftConnectionApi
                        .getCalendarEvent(tenantId, mailboxUserId, event.eventId())
                        .orElse(event);
                if (full.attendees().size() > event.attendees().size()) {
                    log.info(
                            "Enriched calendar attendees mailbox={} eventId={} deltaAttendees={} fullAttendees={}",
                            mailboxUserId,
                            event.eventId(),
                            event.attendees().size(),
                            full.attendees().size()
                    );
                    out.add(full);
                } else {
                    out.add(event);
                }
            } catch (RuntimeException ex) {
                log.warn(
                        "Attendee enrichment failed mailbox={} eventId={} reason={}",
                        mailboxUserId,
                        event.eventId(),
                        ex.getMessage()
                );
                out.add(event);
            }
        }
        return List.copyOf(out);
    }

    static boolean needsAttendeeEnrichment(CalendarEvent event) {
        // Organizer-only or empty roster is the common sparse delta shape.
        return event.attendees().size() <= 1;
    }

    public record SyncResult(String mailboxUserId, int eventsSynced, boolean recoveredFromEmptyDelta) {
    }
}
