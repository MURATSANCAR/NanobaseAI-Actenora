package com.nanobaseai.actenora.aiprocessing.domain.routing;

import java.util.Objects;

/**
 * Maps pipeline task types to starting model roles.
 */
public final class TaskRoleMapping {

    private TaskRoleMapping() {
    }

    public static ModelRole roleFor(InferenceTaskType taskType, TenantRoutingPolicy policy) {
        Objects.requireNonNull(taskType, "taskType");
        Objects.requireNonNull(policy, "policy");
        return switch (taskType) {
            case CHUNK_EXTRACTION -> ModelRole.FAST_EXTRACTION;
            case CANDIDATE_MERGE, FINAL_NOTE -> ModelRole.QWEN27_FINAL;
            case VALIDATION -> switch (policy.validationModelPreference()) {
                case QWEN27_FINAL -> ModelRole.QWEN27_FINAL;
                case SEPARATE_VALIDATION_MODEL -> ModelRole.VALIDATION;
            };
        };
    }
}
