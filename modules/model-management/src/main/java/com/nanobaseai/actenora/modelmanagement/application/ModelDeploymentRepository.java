package com.nanobaseai.actenora.modelmanagement.application;

import com.nanobaseai.actenora.modelmanagement.domain.ModelDeployment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModelDeploymentRepository {

    Optional<ModelDeployment> findByKey(String deploymentKey);

    boolean existsByKey(String deploymentKey);

    void save(ModelDeployment deployment);

    List<ModelDeployment> findByModelDefinitionId(UUID modelDefinitionId);

    List<ModelDeployment> findAll();
}
