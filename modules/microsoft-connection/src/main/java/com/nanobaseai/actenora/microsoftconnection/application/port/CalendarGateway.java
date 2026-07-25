package com.nanobaseai.actenora.microsoftconnection.application.port;

import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarDeltaPage;
import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarEvent;
import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarSyncCursor;

import java.util.Optional;
import java.util.UUID;

/**
 * Microsoft Graph calendar delta / event reads.
 */
public interface CalendarGateway {

    CalendarDeltaPage syncDelta(UUID tenantId, String userId, CalendarSyncCursor cursor);

    Optional<CalendarEvent> getEvent(UUID tenantId, String userId, String eventId);
}
