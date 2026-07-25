package com.nanobaseai.actenora.microsoftconnection.application;

import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarEvent;
import com.nanobaseai.actenora.microsoftconnection.application.model.GraphSubscription;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
        List<GraphSubscription> renewed = subscriptionLifecycleService.renewExpiring();
        List<CalendarEvent> polled = pollingFallbackService.pollAll(mailboxes, (t, u) -> true);
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
