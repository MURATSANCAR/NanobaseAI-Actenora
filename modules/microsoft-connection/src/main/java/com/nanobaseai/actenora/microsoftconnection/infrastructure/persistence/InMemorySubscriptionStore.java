package com.nanobaseai.actenora.microsoftconnection.infrastructure.persistence;

import com.nanobaseai.actenora.microsoftconnection.application.model.GraphSubscription;
import com.nanobaseai.actenora.microsoftconnection.application.port.SubscriptionStore;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemorySubscriptionStore implements SubscriptionStore {

    private final Map<String, GraphSubscription> byId = new ConcurrentHashMap<>();

    @Override
    public void save(GraphSubscription subscription) {
        Objects.requireNonNull(subscription, "subscription");
        byId.put(key(subscription.tenantId(), subscription.subscriptionId()), subscription);
    }

    @Override
    public Optional<GraphSubscription> findById(UUID tenantId, String subscriptionId) {
        return Optional.ofNullable(byId.get(key(tenantId, subscriptionId)));
    }

    @Override
    public Optional<GraphSubscription> findBySubscriptionId(String subscriptionId) {
        return byId.values().stream()
                .filter(subscription -> subscription.subscriptionId().equals(subscriptionId))
                .findFirst();
    }

    @Override
    public List<GraphSubscription> findExpiringBefore(Instant threshold) {
        Objects.requireNonNull(threshold, "threshold");
        return byId.values().stream()
                .filter(s -> s.expiresBefore(threshold))
                .toList();
    }

    @Override
    public List<GraphSubscription> findAllForTenant(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        return byId.values().stream()
                .filter(s -> s.tenantId().equals(tenantId))
                .toList();
    }

    @Override
    public List<UUID> distinctTenantIds() {
        return byId.values().stream()
                .map(GraphSubscription::tenantId)
                .distinct()
                .toList();
    }

    private static String key(UUID tenantId, String subscriptionId) {
        return tenantId + "|" + subscriptionId;
    }
}
