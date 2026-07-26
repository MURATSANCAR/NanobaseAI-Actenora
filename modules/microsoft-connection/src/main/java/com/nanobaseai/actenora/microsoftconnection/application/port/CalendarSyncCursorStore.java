package com.nanobaseai.actenora.microsoftconnection.application.port;

import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarSyncCursor;

import java.util.Optional;
import java.util.UUID;

/**
 * Persists per-tenant / per-mailbox calendar delta cursors.
 */
public interface CalendarSyncCursorStore {

    Optional<CalendarSyncCursor> find(UUID tenantId, String userId);

    void save(CalendarSyncCursor cursor);

    void delete(UUID tenantId, String userId);
}
