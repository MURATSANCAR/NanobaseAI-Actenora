package com.nanobaseai.actenora.microsoftconnection.application;

import com.nanobaseai.actenora.microsoftconnection.application.model.GraphChangeNotification;
import com.nanobaseai.actenora.microsoftconnection.application.model.GraphSubscription;
import com.nanobaseai.actenora.microsoftconnection.application.model.LifecycleNotification;
import com.nanobaseai.actenora.microsoftconnection.application.model.SubscriptionCreateRequest;
import com.nanobaseai.actenora.microsoftconnection.application.port.NotificationInbox;
import com.nanobaseai.actenora.microsoftconnection.application.port.SubscriptionGateway;
import com.nanobaseai.actenora.microsoftconnection.application.port.SubscriptionStore;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Subscription create/renew plus change & lifecycle notification handling with idempotency.
 */
public final class SubscriptionLifecycleService {

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
        List<GraphSubscription> renewed = new ArrayList<>();
        for (GraphSubscription subscription : subscriptionStore.findExpiringBefore(clock.now().plus(renewBeforeExpiry))) {
            GraphSubscription next = subscriptionGateway.renew(
                    subscription.tenantId(),
                    subscription.subscriptionId(),
                    clock.now().plus(renewWindow)
            );
            subscriptionStore.save(next);
            renewed.add(next);
        }
        return List.copyOf(renewed);
    }

    /**
     * @return true if processed, false if duplicate
     */
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
            // Force renew path / polling will catch up; still invoke handler for observability.
        }
        handler.accept(notification);
        return true;
    }
}
