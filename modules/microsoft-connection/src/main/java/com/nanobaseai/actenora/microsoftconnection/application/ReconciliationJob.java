package com.nanobaseai.actenora.microsoftconnection.application;

import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarEvent;
import com.nanobaseai.actenora.microsoftconnection.application.model.GraphSubscription;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Periodic reconciliation: renew subscriptions and poll calendars to heal missed notifications.
 */
public final class ReconciliationJob {

    private final SubscriptionLifecycleService subscriptionLifecycleService;
    private final PollingFallbackService pollingFallbackService;

    public ReconciliationJob(
            SubscriptionLifecycleService subscriptionLifecycleService,
            PollingFallbackService pollingFallbackService
    ) {
        this.subscriptionLifecycleService = Objects.requireNonNull(
                subscriptionLifecycleService, "subscriptionLifecycleService");
        this.pollingFallbackService = Objects.requireNonNull(pollingFallbackService, "pollingFallbackService");
    }

    public ReconciliationResult run(List<PollingFallbackService.MailboxRef> mailboxes) {
        return run(mailboxes, (tenantId, events) -> {
        });
    }

    public ReconciliationResult run(
            List<PollingFallbackService.MailboxRef> mailboxes,
            BiConsumer<UUID, List<CalendarEvent>> eventConsumer
    ) {
        Objects.requireNonNull(mailboxes, "mailboxes");
        Objects.requireNonNull(eventConsumer, "eventConsumer");
        List<GraphSubscription> renewed = List.of();
        List<RuntimeException> failures = new java.util.ArrayList<>();
        try {
            renewed = subscriptionLifecycleService.renewExpiring();
        } catch (RuntimeException ex) {
            failures.add(ex);
        }
        List<CalendarEvent> polled = new java.util.ArrayList<>();
        for (PollingFallbackService.MailboxRef mailbox : mailboxes) {
            try {
                List<CalendarEvent> events = pollingFallbackService.pollMailbox(
                        mailbox.tenantId(), mailbox.userId());
                eventConsumer.accept(mailbox.tenantId(), events);
                polled.addAll(events);
            } catch (RuntimeException ex) {
                failures.add(ex);
            }
        }
        if (!failures.isEmpty()) {
            RuntimeException failure = failures.getFirst();
            failures.stream().skip(1).forEach(failure::addSuppressed);
            throw failure;
        }
        return new ReconciliationResult(renewed.size(), polled.size());
    }

    public record ReconciliationResult(int subscriptionsRenewed, int eventsPolled) {
        public ReconciliationResult {
            if (subscriptionsRenewed < 0 || eventsPolled < 0) {
                throw new IllegalArgumentException("counts must be >= 0");
            }
        }
    }

    public record TenantMailbox(UUID tenantId, String userId) {
        public TenantMailbox {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(userId, "userId");
        }

        public PollingFallbackService.MailboxRef toRef() {
            return new PollingFallbackService.MailboxRef(tenantId, userId);
        }
    }
}
