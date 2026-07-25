package com.nanobaseai.actenora.microsoftconnection.infrastructure.persistence;

import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarSyncCursor;
import com.nanobaseai.actenora.microsoftconnection.application.port.CalendarSyncCursorStore;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryCalendarSyncCursorStore implements CalendarSyncCursorStore {

    private final Map<String, CalendarSyncCursor> byKey = new ConcurrentHashMap<>();

    @Override
    public Optional<CalendarSyncCursor> find(UUID tenantId, String userId) {
        return Optional.ofNullable(byKey.get(key(tenantId, userId)));
    }

    @Override
    public void save(CalendarSyncCursor cursor) {
        Objects.requireNonNull(cursor, "cursor");
        byKey.put(key(cursor.tenantId(), cursor.userId()), cursor);
    }

    private static String key(UUID tenantId, String userId) {
        return tenantId + "|" + userId;
    }
}
