package com.nanobaseai.actenora.microsoftconnection.application;

import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarDeltaPage;
import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarEvent;
import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarSyncCursor;
import com.nanobaseai.actenora.microsoftconnection.application.port.CalendarGateway;
import com.nanobaseai.actenora.microsoftconnection.application.port.CalendarSyncCursorStore;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Advances calendar sync cursors via Graph delta queries.
 * Cursor is persisted only after each page has been successfully processed (process-then-advance).
 */
public final class CalendarSyncService {

    private final CalendarGateway calendarGateway;
    private final CalendarSyncCursorStore cursorStore;
    private final InstantClock clock;

    public CalendarSyncService(
            CalendarGateway calendarGateway,
            CalendarSyncCursorStore cursorStore,
            InstantClock clock
    ) {
        this.calendarGateway = Objects.requireNonNull(calendarGateway, "calendarGateway");
        this.cursorStore = Objects.requireNonNull(cursorStore, "cursorStore");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Syncs the mailbox and collects all events. Cursor advances only after each page is appended
     * to the result list (callers that need upsert-before-advance must use
     * {@link #syncMailbox(UUID, String, Consumer)}).
     */
    public List<CalendarEvent> syncMailbox(UUID tenantId, String userId) {
        List<CalendarEvent> all = new ArrayList<>();
        syncMailbox(tenantId, userId, all::addAll);
        return List.copyOf(all);
    }

    /**
     * Syncs the mailbox page by page. Invokes {@code onPage} for each delta page, then persists
     * the cursor only if {@code onPage} returns normally. If {@code onPage} throws, the cursor
     * is left unchanged so the same page can be retried.
     *
     * @return total number of events passed to {@code onPage}
     */
    public int syncMailbox(UUID tenantId, String userId, Consumer<List<CalendarEvent>> onPage) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(onPage, "onPage");
        CalendarSyncCursor cursor = cursorStore.find(tenantId, userId)
                .orElseGet(() -> CalendarSyncCursor.initial(tenantId, userId, clock.now()));
        AtomicInteger total = new AtomicInteger();
        CalendarDeltaPage page;
        do {
            page = calendarGateway.syncDelta(tenantId, userId, cursor);
            List<CalendarEvent> events = List.copyOf(page.events());
            onPage.accept(events);
            total.addAndGet(events.size());
            cursor = cursor.withPage(page.nextLink(), page.deltaLink(), clock.now());
            cursorStore.save(cursor);
        } while (page.hasMore());
        return total.get();
    }

    public Optional<CalendarEvent> getEvent(UUID tenantId, String userId, String eventId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(eventId, "eventId");
        return calendarGateway.getEvent(tenantId, userId, eventId);
    }
}
