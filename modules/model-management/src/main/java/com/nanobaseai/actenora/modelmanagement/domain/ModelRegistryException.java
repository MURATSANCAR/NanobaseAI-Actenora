package com.nanobaseai.actenora.modelmanagement.domain;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

/**
 * Domain/application errors for the model registry.
 */
public final class ModelRegistryException extends ActenoraException {

    public ModelRegistryException(String code, String message) {
        super(code, message);
    }

    public static ModelRegistryException duplicateModelKey(String modelKey) {
        return new ModelRegistryException(
                "DUPLICATE_MODEL_KEY",
                "Model key already registered: " + modelKey
        );
    }

    public static ModelRegistryException duplicateDeploymentKey(String deploymentKey) {
        return new ModelRegistryException(
                "DUPLICATE_DEPLOYMENT_KEY",
                "Deployment key already registered: " + deploymentKey
        );
    }

    public static ModelRegistryException modelNotFound(String modelKey) {
        return new ModelRegistryException(
                "MODEL_NOT_FOUND",
                "Model not found: " + modelKey
        );
    }

    public static ModelRegistryException deploymentNotFound(String deploymentKey) {
        return new ModelRegistryException(
                "DEPLOYMENT_NOT_FOUND",
                "Deployment not found: " + deploymentKey
        );
    }

    public static ModelRegistryException invalidContextSize(String detail) {
        return new ModelRegistryException("INVALID_CONTEXT_SIZE", detail);
    }

    public static ModelRegistryException permissionDenied(String permission) {
        return new ModelRegistryException(
                "PERMISSION_DENIED",
                "Missing permission: " + permission
        );
    }

    public static ModelRegistryException modelNotAllowedForTenant(String modelKey) {
        return new ModelRegistryException(
                "MODEL_NOT_ALLOWED_FOR_TENANT",
                "Model is not on tenant allowlist: " + modelKey
        );
    }

    public static ModelRegistryException invalidState(String detail) {
        return new ModelRegistryException("INVALID_MODEL_STATE", detail);
    }
}
