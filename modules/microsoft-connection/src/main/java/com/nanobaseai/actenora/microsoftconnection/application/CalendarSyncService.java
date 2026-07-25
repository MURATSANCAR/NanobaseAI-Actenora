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
import java.util.UUID;

/**
 * Advances calendar sync cursors via Graph delta queries.
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

    public List<CalendarEvent> syncMailbox(UUID tenantId, String userId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        CalendarSyncCursor cursor = cursorStore.find(tenantId, userId)
                .orElseGet(() -> CalendarSyncCursor.initial(tenantId, userId, clock.now()));
        List<CalendarEvent> all = new ArrayList<>();
        CalendarDeltaPage page;
        do {
            page = calendarGateway.syncDelta(tenantId, userId, cursor);
            all.addAll(page.events());
            cursor = cursor.withPage(page.nextLink(), page.deltaLink(), clock.now());
            cursorStore.save(cursor);
        } while (page.hasMore());
        return List.copyOf(all);
    }
}
