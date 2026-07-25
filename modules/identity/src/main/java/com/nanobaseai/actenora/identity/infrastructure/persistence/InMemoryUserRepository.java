package com.nanobaseai.actenora.identity.infrastructure.persistence;

import com.nanobaseai.actenora.identity.application.port.UserRepositoryPort;
import com.nanobaseai.actenora.identity.domain.User;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryUserRepository implements UserRepositoryPort {

    private final Map<UUID, User> byId = new ConcurrentHashMap<>();
    private final Map<String, UUID> byEntraObjectId = new ConcurrentHashMap<>();

    @Override
    public Optional<User> findById(UUID userId) {
        return Optional.ofNullable(byId.get(userId));
    }

    @Override
    public Optional<User> findByEntraObjectId(String entraObjectId) {
        UUID id = byEntraObjectId.get(entraObjectId);
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public List<User> listByTenant(TenantId tenantId) {
        return byId.values().stream()
                .filter(user -> user.tenantId().equals(tenantId))
                .sorted((a, b) -> a.email().compareToIgnoreCase(b.email()))
                .toList();
    }

    @Override
    public void save(User user) {
        UUID previousId = byEntraObjectId.put(user.entraObjectId(), user.id());
        if (previousId != null && !previousId.equals(user.id())) {
            throw new IllegalStateException("Duplicate Entra mapping for " + user.entraObjectId());
        }
        byId.put(user.id(), user);
    }

    public void clear() {
        byId.clear();
        byEntraObjectId.clear();
    }
}
