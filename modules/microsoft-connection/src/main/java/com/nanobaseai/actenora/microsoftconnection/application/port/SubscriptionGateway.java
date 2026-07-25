package com.nanobaseai.actenora.microsoftconnection.application.port;

import com.nanobaseai.actenora.microsoftconnection.application.model.GraphSubscription;
import com.nanobaseai.actenora.microsoftconnection.application.model.SubscriptionCreateRequest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Microsoft Graph change notification subscriptions (create / renew).
 */
public interface SubscriptionGateway {

    GraphSubscription create(UUID tenantId, SubscriptionCreateRequest request);

    GraphSubscription renew(UUID tenantId, String subscriptionId, Instant newExpiration);

    Optional<GraphSubscription> get(UUID tenantId, String subscriptionId);

    void delete(UUID tenantId, String subscriptionId);
}
