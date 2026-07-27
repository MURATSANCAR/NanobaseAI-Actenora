package com.nanobaseai.actenora.microsoftconnection.application;

import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Polling fallback when Graph subscriptions miss change notifications.
 */
public final class PollingFallbackService {

    private final CalendarSyncService calendarSyncService;

    public PollingFallbackService(CalendarSyncService calendarSyncService) {
        this.calendarSyncService = Objects.requireNonNull(calendarSyncService, "calendarSyncService");
    }

    public List<CalendarEvent> pollMailbox(UUID tenantId, String userId) {
        return calendarSyncService.syncMailbox(tenantId, userId);
    }

    /**
     * Polls with process-then-advance: {@code onPage} runs before the delta cursor is saved.
     *
     * @return total events processed
     */
    public int pollMailbox(UUID tenantId, String userId, Consumer<List<CalendarEvent>> onPage) {
        return calendarSyncService.syncMailbox(tenantId, userId, onPage);
    }

    public List<CalendarEvent> pollAll(
            List<MailboxRef> mailboxes,
            BiFunction<UUID, String, Boolean> shouldPoll
    ) {
        Objects.requireNonNull(mailboxes, "mailboxes");
        List<CalendarEvent> events = new ArrayList<>();
        for (MailboxRef mailbox : mailboxes) {
            if (shouldPoll != null && !Boolean.TRUE.equals(shouldPoll.apply(mailbox.tenantId(), mailbox.userId()))) {
                continue;
            }
            events.addAll(pollMailbox(mailbox.tenantId(), mailbox.userId()));
        }
        return List.copyOf(events);
    }

    public record MailboxRef(UUID tenantId, String userId) {
        public MailboxRef {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(userId, "userId");
        }
    }
}
