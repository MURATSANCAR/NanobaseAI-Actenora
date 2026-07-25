package com.nanobaseai.actenora.modelmanagement.application;

import com.nanobaseai.actenora.modelmanagement.domain.ModelDefinition;

import java.util.List;
import java.util.Optional;

public interface ModelDefinitionRepository {

    Optional<ModelDefinition> findByKey(String modelKey);

    Optional<ModelDefinition> findById(java.util.UUID id);

    boolean existsByKey(String modelKey);

    void save(ModelDefinition definition);

    List<ModelDefinition> findAll();
}
