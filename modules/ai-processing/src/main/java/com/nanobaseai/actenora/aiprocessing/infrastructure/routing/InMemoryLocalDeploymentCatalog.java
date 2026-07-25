package com.nanobaseai.actenora.aiprocessing.infrastructure.routing;

import com.nanobaseai.actenora.aiprocessing.application.port.LocalDeploymentCatalogPort;
import com.nanobaseai.actenora.aiprocessing.domain.routing.LocalDeploymentRef;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ModelRole;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryLocalDeploymentCatalog implements LocalDeploymentCatalogPort {

    private final Map<UUID, LocalDeploymentRef> byId = new ConcurrentHashMap<>();

    @Override
    public List<LocalDeploymentRef> listLocalDeployments() {
        return byId.values().stream()
                .sorted(Comparator.comparingInt(LocalDeploymentRef::priority)
                        .thenComparing(LocalDeploymentRef::deploymentKey))
                .toList();
    }

    @Override
    public Optional<LocalDeploymentRef> findByDeploymentId(UUID deploymentId) {
        return Optional.ofNullable(byId.get(deploymentId));
    }

    @Override
    public List<LocalDeploymentRef> findByRole(ModelRole role) {
        return listLocalDeployments().stream().filter(d -> d.role() == role).toList();
    }

    @Override
    public void upsert(LocalDeploymentRef deployment) {
        byId.put(deployment.deploymentId(), deployment);
    }

    @Override
    public void markHealthy(UUID deploymentId, boolean healthy) {
        LocalDeploymentRef current = byId.get(deploymentId);
        if (current == null) {
            throw new IllegalArgumentException("unknown deployment " + deploymentId);
        }
        byId.put(deploymentId, new LocalDeploymentRef(
                current.deploymentId(),
                current.modelDefinitionId(),
                current.modelKey(),
                current.deploymentKey(),
                current.role(),
                current.qualityScore(),
                healthy,
                current.mock(),
                current.priority()));
    }

    public void clear() {
        byId.clear();
    }

    public List<LocalDeploymentRef> snapshot() {
        return new ArrayList<>(listLocalDeployments());
    }
}
