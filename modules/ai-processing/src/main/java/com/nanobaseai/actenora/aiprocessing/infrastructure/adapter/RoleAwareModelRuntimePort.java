package com.nanobaseai.actenora.aiprocessing.infrastructure.adapter;

import com.nanobaseai.actenora.aiprocessing.application.pipeline.InferenceRequest;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.InferenceResponse;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ModelDescriptor;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ModelRuntimePort;
import com.nanobaseai.actenora.aiprocessing.domain.routing.InferenceTaskType;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ModelRole;
import com.nanobaseai.actenora.aiprocessing.domain.routing.TaskRoleMapping;
import com.nanobaseai.actenora.aiprocessing.domain.routing.TenantRoutingPolicy;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Routes inference to FAST vs FINAL model runtimes by {@link InferenceTaskType}.
 */
public final class RoleAwareModelRuntimePort implements ModelRuntimePort {

    private final ModelRuntimePort fastRuntime;
    private final ModelRuntimePort finalRuntime;
    private final Supplier<TenantRoutingPolicy> policySupplier;
    private final Map<ModelRole, ModelRuntimePort> byRole;

    public RoleAwareModelRuntimePort(
            ModelRuntimePort fastRuntime,
            ModelRuntimePort finalRuntime,
            Supplier<TenantRoutingPolicy> policySupplier
    ) {
        this.fastRuntime = Objects.requireNonNull(fastRuntime, "fastRuntime");
        this.finalRuntime = Objects.requireNonNull(finalRuntime, "finalRuntime");
        this.policySupplier = Objects.requireNonNull(policySupplier, "policySupplier");
        this.byRole = new EnumMap<>(ModelRole.class);
        this.byRole.put(ModelRole.FAST_EXTRACTION, fastRuntime);
        this.byRole.put(ModelRole.QWEN27_FINAL, finalRuntime);
        this.byRole.put(ModelRole.VALIDATION, finalRuntime);
    }

    @Override
    public ModelDescriptor descriptor() {
        // Default descriptor for chunking budget — use the larger final context window.
        return finalRuntime.descriptor();
    }

    public ModelDescriptor descriptorFor(InferenceTaskType taskType) {
        return runtimeFor(taskType).descriptor();
    }

    @Override
    public boolean healthy() {
        return fastRuntime.healthy() || finalRuntime.healthy();
    }

    public boolean healthyFor(InferenceTaskType taskType) {
        return runtimeFor(taskType).healthy();
    }

    @Override
    public InferenceResponse infer(InferenceRequest request) {
        InferenceTaskType taskType;
        try {
            taskType = InferenceTaskType.valueOf(request.taskType());
        } catch (RuntimeException ex) {
            taskType = InferenceTaskType.FINAL_NOTE;
        }
        return runtimeFor(taskType).infer(request);
    }

    private ModelRuntimePort runtimeFor(InferenceTaskType taskType) {
        ModelRole role = TaskRoleMapping.roleFor(taskType, policySupplier.get());
        ModelRuntimePort selected = byRole.get(role);
        return selected == null ? finalRuntime : selected;
    }
}
