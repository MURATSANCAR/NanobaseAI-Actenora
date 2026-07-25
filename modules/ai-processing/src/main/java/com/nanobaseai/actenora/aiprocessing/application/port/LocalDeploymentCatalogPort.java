package com.nanobaseai.actenora.aiprocessing.application.port;

import com.nanobaseai.actenora.aiprocessing.domain.routing.LocalDeploymentRef;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ModelRole;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read port over local model deployments (backed by model-management catalog / mock seed).
 */
public interface LocalDeploymentCatalogPort {

    List<LocalDeploymentRef> listLocalDeployments();

    Optional<LocalDeploymentRef> findByDeploymentId(UUID deploymentId);

    List<LocalDeploymentRef> findByRole(ModelRole role);

    void upsert(LocalDeploymentRef deployment);

    void markHealthy(UUID deploymentId, boolean healthy);
}
