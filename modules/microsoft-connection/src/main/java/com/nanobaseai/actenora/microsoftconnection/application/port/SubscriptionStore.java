package com.nanobaseai.actenora.microsoftconnection.application.port;

import com.nanobaseai.actenora.microsoftconnection.application.model.GraphSubscription;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Local registry of Graph subscriptions owned by Actenora.
 */
public interface SubscriptionStore {

    void save(GraphSubscription subscription);

    Optional<GraphSubscription> findById(UUID tenantId, String subscriptionId);

    List<GraphSubscription> findExpiringBefore(Instant threshold);

    List<GraphSubscription> findAllForTenant(UUID tenantId);
}
