package com.nanobaseai.actenora.aiprocessing.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the served model identity a provider expects for a routed model definition.
 * Backed by the model registry; callers fall back to the routed model key.
 */
public interface ServedModelResolverPort {

    Optional<String> findServedModelId(UUID modelDefinitionId);

    static ServedModelResolverPort none() {
        return modelDefinitionId -> Optional.empty();
    }
}
