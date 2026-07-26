package com.nanobaseai.actenora.microsoftconnection.application;

import com.nanobaseai.actenora.microsoftconnection.application.model.GraphChangeNotification;
import com.nanobaseai.actenora.microsoftconnection.application.model.GraphSubscription;
import com.nanobaseai.actenora.microsoftconnection.application.model.LifecycleNotification;
import com.nanobaseai.actenora.microsoftconnection.application.model.SubscriptionCreateRequest;
import com.nanobaseai.actenora.microsoftconnection.application.port.NotificationInbox;
import com.nanobaseai.actenora.microsoftconnection.application.port.SubscriptionGateway;
import com.nanobaseai.actenora.microsoftconnection.application.port.SubscriptionStore;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Subscription create/renew plus change & lifecycle notification handling with idempotency.
 *
 * <p>Not {@code final}: Spring must CGLIB-proxy this bean so {@code @Transactional} on
 * notification handlers actually opens a TX around claim + outbox enqueue.
 */
public class SubscriptionLifecycleService {

    public static final String CONSUMER_CHANGE = "graph-change-notification";
    public static final String CONSUMER_LIFECYCLE = "graph-lifecycle-notification";

    private final SubscriptionGateway subscriptionGateway;
    private final SubscriptionStore subscriptionStore;
    private final NotificationInbox notificationInbox;
    private final InstantClock clock;
    private final Duration renewBeforeExpiry;
    private final Duration renewWindow;

    public SubscriptionLifecycleService(
            SubscriptionGateway subscriptionGateway,
            SubscriptionStore subscriptionStore,
            NotificationInbox notificationInbox,
            InstantClock clock,
            Duration renewBeforeExpiry,
            Duration renewWindow
    ) {
        this.subscriptionGateway = Objects.requireNonNull(subscriptionGateway, "subscriptionGateway");
        this.subscriptionStore = Objects.requireNonNull(subscriptionStore, "subscriptionStore");
        this.notificationInbox = Objects.requireNonNull(notificationInbox, "notificationInbox");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.renewBeforeExpiry = Objects.requireNonNull(renewBeforeExpiry, "renewBeforeExpiry");
        this.renewWindow = Objects.requireNonNull(renewWindow, "renewWindow");
    }

    public GraphSubscription create(UUID tenantId, SubscriptionCreateRequest request) {
        GraphSubscription created = subscriptionGateway.create(tenantId, request);
        subscriptionStore.save(created);
        return created;
    }

    public List<GraphSubscription> renewExpiring() {
        RenewBatchResult result = renewExpiringBatch();
        if (result.failureCount() > 0) {
            RuntimeException failure = result.failures().getFirst();
            result.failures().stream().skip(1).forEach(failure::addSuppressed);
            throw failure;
        }
        return result.renewed();
    }

    /**
     * Renews expiring subscriptions without aborting the batch on the first failure.
     */
    public RenewBatchResult renewExpiringBatch() {
        List<GraphSubscription> renewed = new ArrayList<>();
        List<RuntimeException> failures = new ArrayList<>();
        for (GraphSubscription subscription : subscriptionStore.findExpiringBefore(clock.now().plus(renewBeforeExpiry))) {
            try {
                GraphSubscription next = subscriptionGateway.renew(
                        subscription.tenantId(),
                        subscription.subscriptionId(),
                        clock.now().plus(renewWindow)
                );
                subscriptionStore.save(next);
                renewed.add(next);
            } catch (RuntimeException ex) {
                failures.add(ex);
            }
        }
        return new RenewBatchResult(List.copyOf(renewed), List.copyOf(failures));
    }

    public record RenewBatchResult(List<GraphSubscription> renewed, List<RuntimeException> failures) {
        public RenewBatchResult {
            renewed = List.copyOf(Objects.requireNonNull(renewed, "renewed"));
            failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
        }

        public int failureCount() {
            return failures.size();
        }
    }

    /**
     * @return true if processed, false if duplicate
     */
    @Transactional
    public boolean handleChangeNotification(
            GraphChangeNotification notification,
            Consumer<GraphChangeNotification> handler
    ) {
        Objects.requireNonNull(notification, "notification");
        Objects.requireNonNull(handler, "handler");
        if (!notificationInbox.claim(CONSUMER_CHANGE, notification.notificationId())) {
            return false;
        }
        handler.accept(notification);
        return true;
    }

    /**
     * @return true if processed, false if duplicate
     */
    @Transactional
    public boolean handleLifecycleNotification(
            LifecycleNotification notification,
            Consumer<LifecycleNotification> handler
    ) {
        Objects.requireNonNull(notification, "notification");
        Objects.requireNonNull(handler, "handler");
        if (!notificationInbox.claim(CONSUMER_LIFECYCLE, notification.notificationId())) {
            return false;
        }
        if (notification.requiresReauthorization() || notification.missed()) {
            renewExpiring();
        }
        handler.accept(notification);
        return true;
    }
}
