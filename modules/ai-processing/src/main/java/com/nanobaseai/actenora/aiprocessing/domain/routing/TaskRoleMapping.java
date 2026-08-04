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
            case CHUNK_EXTRACTION, MEETING_TRIAGE -> ModelRole.FAST_EXTRACTION;
            case CANDIDATE_MERGE, FINAL_NOTE -> ModelRole.PRIMARY_QUALITY;
            case NORMALIZE, CHUNK_PLAN, EMBEDDING -> ModelRole.FAST_EXTRACTION;
            case VALIDATION -> switch (policy.validationModelPreference()) {
                case PRIMARY_QUALITY -> ModelRole.PRIMARY_QUALITY;
                case SEPARATE_VALIDATION_MODEL -> ModelRole.VALIDATION;
            };
        };
    }
}
