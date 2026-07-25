package com.nanobaseai.actenora.modelmanagement.infrastructure;

import com.nanobaseai.actenora.modelmanagement.application.ModelDeploymentRepository;
import com.nanobaseai.actenora.modelmanagement.domain.ModelDeployment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class InMemoryModelDeploymentRepository implements ModelDeploymentRepository {

    private final Map<String, ModelDeployment> byKey = new ConcurrentHashMap<>();

    @Override
    public Optional<ModelDeployment> findByKey(String deploymentKey) {
        return Optional.ofNullable(byKey.get(deploymentKey));
    }

    @Override
    public boolean existsByKey(String deploymentKey) {
        return byKey.containsKey(deploymentKey);
    }

    @Override
    public void save(ModelDeployment deployment) {
        byKey.put(deployment.deploymentKey(), deployment);
    }

    @Override
    public List<ModelDeployment> findByModelDefinitionId(UUID modelDefinitionId) {
        return byKey.values().stream()
                .filter(d -> d.modelDefinitionId().equals(modelDefinitionId))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public List<ModelDeployment> findAll() {
        return List.copyOf(new ArrayList<>(byKey.values()));
    }
}
