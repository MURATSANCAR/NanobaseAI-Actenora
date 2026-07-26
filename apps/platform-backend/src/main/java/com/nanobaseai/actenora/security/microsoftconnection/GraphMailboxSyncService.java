package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.microsoftconnection.api.MicrosoftConnectionApi;
import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarEvent;
import com.nanobaseai.actenora.microsoftconnection.application.port.CalendarSyncCursorStore;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Pulls Graph calendar delta for a mailbox and upserts Actenora meetings.
 */
public final class GraphMailboxSyncService {

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
        List<CalendarEvent> events = microsoftConnectionApi.syncCalendar(tenantId, mailboxUserId);
        calendarMeetingUpsertAdapter.upsertEvents(TenantId.of(tenantId), events);
        microsoftConnectionApi.ensureTranscriptionForCalendarEvents(tenantId, mailboxUserId, events);
        return new SyncResult(mailboxUserId, events.size(), false);
    }

    public record SyncResult(String mailboxUserId, int eventsSynced, boolean recoveredFromEmptyDelta) {
    }
}
