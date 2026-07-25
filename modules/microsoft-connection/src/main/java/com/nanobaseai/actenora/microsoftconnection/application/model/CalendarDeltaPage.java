package com.nanobaseai.actenora.microsoftconnection.application.model;

import java.util.List;
import java.util.Objects;

/**
 * One page of calendar delta results plus updated cursor links.
 */
public record CalendarDeltaPage(
        List<CalendarEvent> events,
        String nextLink,
        String deltaLink
) {

    public CalendarDeltaPage {
        Objects.requireNonNull(events, "events");
        events = List.copyOf(events);
    }

    public boolean hasMore() {
        return nextLink != null && !nextLink.isBlank();
    }
}
