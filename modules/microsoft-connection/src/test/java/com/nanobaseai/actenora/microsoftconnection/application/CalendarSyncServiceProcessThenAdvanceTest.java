package com.nanobaseai.actenora.microsoftconnection.application;

import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarDeltaPage;
import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarEvent;
import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarSyncCursor;
import com.nanobaseai.actenora.microsoftconnection.application.model.ParticipantMetadata;
import com.nanobaseai.actenora.microsoftconnection.application.port.CalendarGateway;
import com.nanobaseai.actenora.microsoftconnection.domain.identity.ImmutableGraphEventIdentity;
import com.nanobaseai.actenora.microsoftconnection.domain.identity.SeriesOccurrenceKind;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.persistence.InMemoryCalendarSyncCursorStore;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalendarSyncServiceProcessThenAdvanceTest {

    private final UUID tenantId = UUID.randomUUID();
    private final String userId = "user-1";
    private final Instant fixedNow = Instant.parse("2026-07-27T10:00:00Z");
    private final InstantClock clock = new InstantClock(Clock.fixed(fixedNow, ZoneOffset.UTC));

    @Test
    void savesCursorOnlyAfterOnPageSucceeds() {
        InMemoryCalendarSyncCursorStore cursorStore = new InMemoryCalendarSyncCursorStore();
        ScriptedGateway gateway = new ScriptedGateway(List.of(
                new CalendarDeltaPage(List.of(event("e1")), null, "https://graph/delta?token=final")
        ));
        CalendarSyncService sync = new CalendarSyncService(gateway, cursorStore, clock);

        int count = sync.syncMailbox(tenantId, userId, page -> assertEquals(1, page.size()));

        assertEquals(1, count);
        assertTrue(cursorStore.find(tenantId, userId).isPresent());
        assertEquals("https://graph/delta?token=final", cursorStore.find(tenantId, userId).orElseThrow().deltaLink());
    }

    @Test
    void doesNotAdvanceCursorWhenOnPageThrows() {
        InMemoryCalendarSyncCursorStore cursorStore = new InMemoryCalendarSyncCursorStore();
        ScriptedGateway gateway = new ScriptedGateway(List.of(
                new CalendarDeltaPage(List.of(event("e1")), null, "https://graph/delta?token=lost")
        ));
        CalendarSyncService sync = new CalendarSyncService(gateway, cursorStore, clock);

        assertThrows(IllegalStateException.class, () ->
                sync.syncMailbox(tenantId, userId, page -> {
                    throw new IllegalStateException("upsert failed");
                }));

        assertTrue(cursorStore.find(tenantId, userId).isEmpty());
    }

    @Test
    void advancesFirstPageCursorAndRetriesSecondPageAfterUpsertFailure() {
        InMemoryCalendarSyncCursorStore cursorStore = new InMemoryCalendarSyncCursorStore();
        ScriptedGateway gateway = new ScriptedGateway(List.of(
                new CalendarDeltaPage(List.of(event("e1")), "https://graph/next?page=2", null),
                new CalendarDeltaPage(List.of(event("e2")), null, "https://graph/delta?token=done"),
                // retry after failure resumes from saved nextLink (page 2 again)
                new CalendarDeltaPage(List.of(event("e2")), null, "https://graph/delta?token=done")
        ));
        CalendarSyncService sync = new CalendarSyncService(gateway, cursorStore, clock);
        AtomicInteger pageCalls = new AtomicInteger();

        assertThrows(IllegalStateException.class, () ->
                sync.syncMailbox(tenantId, userId, page -> {
                    int n = pageCalls.incrementAndGet();
                    if (n == 2) {
                        throw new IllegalStateException("page 2 upsert failed");
                    }
                }));

        CalendarSyncCursor afterFail = cursorStore.find(tenantId, userId).orElseThrow();
        assertEquals("https://graph/next?page=2", afterFail.nextLink());

        List<String> retriedIds = new ArrayList<>();
        int recovered = sync.syncMailbox(tenantId, userId, page ->
                page.forEach(e -> retriedIds.add(e.eventId())));

        assertEquals(1, recovered);
        assertEquals(List.of("e2"), retriedIds);
        assertEquals("https://graph/delta?token=done", cursorStore.find(tenantId, userId).orElseThrow().deltaLink());
    }

    private static CalendarEvent event(String id) {
        Instant start = Instant.parse("2026-07-27T09:00:00Z");
        return new CalendarEvent(
                new ImmutableGraphEventIdentity(id),
                id,
                null,
                "ical-" + id,
                SeriesOccurrenceKind.SINGLE,
                "Meeting " + id,
                start,
                start.plusSeconds(3600),
                null,
                null,
                null,
                false,
                List.of(new ParticipantMetadata("a@example.com", "A", "a@example.com", "required", "a@example.com"))
        );
    }

    private static final class ScriptedGateway implements CalendarGateway {
        private final List<CalendarDeltaPage> pages;
        private int index;

        private ScriptedGateway(List<CalendarDeltaPage> pages) {
            this.pages = pages;
        }

        @Override
        public CalendarDeltaPage syncDelta(UUID tenantId, String userId, CalendarSyncCursor cursor) {
            if (index >= pages.size()) {
                throw new IllegalStateException("no more scripted pages");
            }
            return pages.get(index++);
        }

        @Override
        public Optional<CalendarEvent> getEvent(UUID tenantId, String userId, String eventId) {
            return Optional.empty();
        }
    }
}
