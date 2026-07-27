package com.nanobaseai.actenora.microsoftconnection.application;

import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarEvent;
import com.nanobaseai.actenora.microsoftconnection.application.model.GraphSubscription;

import java.util.ArrayList;
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
        return run(mailboxes, (mailbox, events) -> {
        });
    }

    public ReconciliationResult run(
            List<PollingFallbackService.MailboxRef> mailboxes,
            BiConsumer<PollingFallbackService.MailboxRef, List<CalendarEvent>> eventConsumer
    ) {
        Objects.requireNonNull(mailboxes, "mailboxes");
        Objects.requireNonNull(eventConsumer, "eventConsumer");

        SubscriptionLifecycleService.RenewBatchResult renew =
                subscriptionLifecycleService.renewExpiringBatch();

        List<CalendarEvent> polled = new ArrayList<>();
        List<RuntimeException> pollFailures = new ArrayList<>();
        for (PollingFallbackService.MailboxRef mailbox : mailboxes) {
            try {
                pollingFallbackService.pollMailbox(
                        mailbox.tenantId(),
                        mailbox.userId(),
                        events -> {
                            eventConsumer.accept(mailbox, events);
                            polled.addAll(events);
                        });
            } catch (RuntimeException ex) {
                pollFailures.add(ex);
            }
        }

        ReconciliationResult result = new ReconciliationResult(
                renew.renewed().size(),
                renew.failureCount(),
                polled.size(),
                pollFailures.size()
        );

        if (result.hasFailures()) {
            List<RuntimeException> failures = new ArrayList<>(renew.failures());
            failures.addAll(pollFailures);
            RuntimeException failure = failures.getFirst();
            failures.stream().skip(1).forEach(failure::addSuppressed);
            throw new ReconciliationFailedException(result, failure);
        }
        return result;
    }

    public record ReconciliationResult(
            int subscriptionsRenewed,
            int subscriptionRenewFailures,
            int eventsPolled,
            int mailboxPollFailures
    ) {
        public ReconciliationResult {
            if (subscriptionsRenewed < 0
                    || subscriptionRenewFailures < 0
                    || eventsPolled < 0
                    || mailboxPollFailures < 0) {
                throw new IllegalArgumentException("counts must be >= 0");
            }
        }

        public boolean hasFailures() {
            return subscriptionRenewFailures > 0 || mailboxPollFailures > 0;
        }
    }

    public static final class ReconciliationFailedException extends RuntimeException {
        private final ReconciliationResult result;

        public ReconciliationFailedException(ReconciliationResult result, Throwable cause) {
            super(cause.getMessage(), cause);
            this.result = Objects.requireNonNull(result, "result");
        }

        public ReconciliationResult result() {
            return result;
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
