package com.nanobaseai.actenora.modelmanagement.infrastructure;

import com.nanobaseai.actenora.modelmanagement.application.ModelDefinitionRepository;
import com.nanobaseai.actenora.modelmanagement.domain.ModelDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryModelDefinitionRepository implements ModelDefinitionRepository {

    private final Map<String, ModelDefinition> byKey = new ConcurrentHashMap<>();
    private final Map<UUID, ModelDefinition> byId = new ConcurrentHashMap<>();

    @Override
    public Optional<ModelDefinition> findByKey(String modelKey) {
        return Optional.ofNullable(byKey.get(modelKey));
    }

    @Override
    public Optional<ModelDefinition> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public boolean existsByKey(String modelKey) {
        return byKey.containsKey(modelKey);
    }

    @Override
    public void save(ModelDefinition definition) {
        byKey.put(definition.modelKey(), definition);
        byId.put(definition.id(), definition);
    }

    @Override
    public List<ModelDefinition> findAll() {
        return List.copyOf(new ArrayList<>(byKey.values()));
    }
}
