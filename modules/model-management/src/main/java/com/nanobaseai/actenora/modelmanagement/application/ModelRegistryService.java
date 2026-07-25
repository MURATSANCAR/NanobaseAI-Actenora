package com.nanobaseai.actenora.modelmanagement.application;

import com.nanobaseai.actenora.modelmanagement.domain.ModelCapability;
import com.nanobaseai.actenora.modelmanagement.domain.ModelDefinition;
import com.nanobaseai.actenora.modelmanagement.domain.ModelDeployment;
import com.nanobaseai.actenora.modelmanagement.domain.ModelRegistryException;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Application service for the AI control-plane model / capability / deployment registry.
 */
public final class ModelRegistryService {

    private final ModelDefinitionRepository modelDefinitions;
    private final ModelDeploymentRepository deployments;
    private final ModelControlPermissionPort permissions;
    private final TenantModelAllowlistPort allowlist;
    private final ModelRegistryAuditPort audit;
    private final DeploymentHealthSettings healthSettings;
    private final InstantClock clock;

    public ModelRegistryService(
            ModelDefinitionRepository modelDefinitions,
            ModelDeploymentRepository deployments,
            ModelControlPermissionPort permissions,
            TenantModelAllowlistPort allowlist,
            ModelRegistryAuditPort audit,
            DeploymentHealthSettings healthSettings,
            InstantClock clock
    ) {
        this.modelDefinitions = Objects.requireNonNull(modelDefinitions, "modelDefinitions");
        this.deployments = Objects.requireNonNull(deployments, "deployments");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.allowlist = Objects.requireNonNull(allowlist, "allowlist");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.healthSettings = Objects.requireNonNull(healthSettings, "healthSettings");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ModelDefinitionView registerModel(ActorPrincipal actor, RegisterModelCommand command) {
        permissions.require(actor, ModelControlPermission.MODEL_REGISTER);
        if (modelDefinitions.existsByKey(command.modelKey())) {
            throw ModelRegistryException.duplicateModelKey(command.modelKey());
        }
        Instant now = clock.now();
        ModelDefinition definition = ModelDefinition.register(
                command.modelKey(),
                command.displayName(),
                command.providerType(),
                command.servedModelId(),
                command.modelFamily(),
                command.parameterSize(),
                command.quantization(),
                command.contextWindow(),
                command.maxOutputTokens(),
                command.supportedLanguages(),
                command.priority(),
                command.qualityScore(),
                command.speedScore(),
                now
        );
        modelDefinitions.save(definition);
        audit.append(
                actor.userId().toString(),
                "MODEL_REGISTERED",
                "ModelDefinition",
                definition.modelKey(),
                Map.of(
                        "providerType", definition.providerType(),
                        "contextWindow", definition.contextWindow(),
                        "status", definition.status().name()
                ),
                now
        );
        return toView(definition);
    }

    public ModelDefinitionView updateModel(ActorPrincipal actor, String modelKey, UpdateModelCommand command) {
        permissions.require(actor, ModelControlPermission.MODEL_UPDATE);
        ModelDefinition definition = requireModel(modelKey);
        Map<String, Object> before = snapshot(definition);
        Instant now = clock.now();
        definition.update(
                command.displayName(),
                command.providerType(),
                command.servedModelId(),
                command.modelFamily(),
                command.parameterSize(),
                command.quantization(),
                command.contextWindow(),
                command.maxOutputTokens(),
                command.supportedLanguages(),
                command.priority(),
                command.qualityScore(),
                command.speedScore(),
                now
        );
        modelDefinitions.save(definition);
        audit.append(
                actor.userId().toString(),
                "MODEL_UPDATED",
                "ModelDefinition",
                definition.modelKey(),
                Map.of("before", before, "after", snapshot(definition)),
                now
        );
        return toView(definition);
    }

    public ModelDefinitionView configureCapability(
            ActorPrincipal actor,
            String modelKey,
            ConfigureCapabilityCommand command
    ) {
        permissions.require(actor, ModelControlPermission.CAPABILITY_CONFIGURE);
        ModelDefinition definition = requireModel(modelKey);
        Instant now = clock.now();
        ModelCapability capability = new ModelCapability(
                command.capability(),
                command.qualityScore(),
                command.speedScore(),
                command.minContextRequired(),
                command.enabled()
        );
        definition.configureCapability(capability, now);
        modelDefinitions.save(definition);
        audit.append(
                actor.userId().toString(),
                "CAPABILITY_CONFIGURED",
                "ModelCapability",
                definition.modelKey() + ":" + command.capability().name(),
                Map.of(
                        "enabled", command.enabled(),
                        "minContextRequired", command.minContextRequired(),
                        "qualityScore", command.qualityScore()
                ),
                now
        );
        return toView(definition);
    }

    public ModelDefinitionView enableModel(ActorPrincipal actor, String modelKey) {
        permissions.require(actor, ModelControlPermission.MODEL_ENABLE_DISABLE);
        ModelDefinition definition = requireModel(modelKey);
        Instant now = clock.now();
        String previous = definition.status().name();
        definition.enable(now);
        modelDefinitions.save(definition);
        audit.append(
                actor.userId().toString(),
                "MODEL_ENABLED",
                "ModelDefinition",
                modelKey,
                Map.of("previousStatus", previous, "status", definition.status().name()),
                now
        );
        return toView(definition);
    }

    public ModelDefinitionView disableModel(ActorPrincipal actor, String modelKey) {
        permissions.require(actor, ModelControlPermission.MODEL_ENABLE_DISABLE);
        ModelDefinition definition = requireModel(modelKey);
        Instant now = clock.now();
        String previous = definition.status().name();
        definition.disable(now);
        modelDefinitions.save(definition);
        audit.append(
                actor.userId().toString(),
                "MODEL_DISABLED",
                "ModelDefinition",
                modelKey,
                Map.of("previousStatus", previous, "status", definition.status().name()),
                now
        );
        return toView(definition);
    }

    public ModelDefinitionView drainModel(ActorPrincipal actor, String modelKey) {
        permissions.require(actor, ModelControlPermission.MODEL_DRAIN);
        ModelDefinition definition = requireModel(modelKey);
        Instant now = clock.now();
        String previous = definition.status().name();
        definition.drain(now);
        modelDefinitions.save(definition);
        // Also drain healthy/registered deployments so routers stop selecting them.
        for (ModelDeployment deployment : deployments.findByModelDefinitionId(definition.id())) {
            if (deployment.acceptsNewWork()) {
                deployment.drain();
                deployments.save(deployment);
            }
        }
        audit.append(
                actor.userId().toString(),
                "MODEL_DRAINED",
                "ModelDefinition",
                modelKey,
                Map.of("previousStatus", previous, "status", definition.status().name()),
                now
        );
        return toView(definition);
    }

    public ModelDeploymentView registerDeployment(ActorPrincipal actor, RegisterDeploymentCommand command) {
        permissions.require(actor, ModelControlPermission.DEPLOYMENT_REGISTER);
        ModelDefinition definition = requireModel(command.modelKey());
        if (deployments.existsByKey(command.deploymentKey())) {
            throw ModelRegistryException.duplicateDeploymentKey(command.deploymentKey());
        }
        Instant now = clock.now();
        ModelDeployment deployment = ModelDeployment.register(
                definition.id(),
                command.deploymentKey(),
                command.endpoint(),
                command.nodeName(),
                command.zone(),
                command.hardwareType(),
                command.gpuType(),
                command.gpuCount(),
                command.cpuCount(),
                command.memoryGb(),
                command.maxConcurrency(),
                now
        );
        deployments.save(deployment);
        audit.append(
                actor.userId().toString(),
                "DEPLOYMENT_REGISTERED",
                "ModelDeployment",
                deployment.deploymentKey(),
                Map.of(
                        "modelKey", definition.modelKey(),
                        "endpoint", deployment.endpoint(),
                        "nodeName", deployment.nodeName()
                ),
                now
        );
        return toDeploymentView(definition.modelKey(), deployment, now);
    }

    public ModelDeploymentView heartbeat(ActorPrincipal actor, String deploymentKey) {
        permissions.require(actor, ModelControlPermission.DEPLOYMENT_HEARTBEAT);
        ModelDeployment deployment = deployments.findByKey(deploymentKey)
                .orElseThrow(() -> ModelRegistryException.deploymentNotFound(deploymentKey));
        Instant now = clock.now();
        deployment.heartbeat(now);
        deployments.save(deployment);
        String modelKey = modelDefinitions.findById(deployment.modelDefinitionId())
                .map(ModelDefinition::modelKey)
                .orElse("unknown");
        return toDeploymentView(modelKey, deployment, now);
    }

    public ModelHealthView healthView(ActorPrincipal actor) {
        permissions.require(actor, ModelControlPermission.HEALTH_VIEW);
        Instant now = clock.now();
        List<ModelHealthView.ModelHealthEntry> entries = new ArrayList<>();
        for (ModelDefinition definition : modelDefinitions.findAll()) {
            List<ModelHealthView.DeploymentHealthEntry> deploymentEntries = new ArrayList<>();
            int healthy = 0;
            int draining = 0;
            int unhealthy = 0;
            for (ModelDeployment deployment : deployments.findByModelDefinitionId(definition.id())) {
                boolean timedOut = deployment.isHeartbeatTimedOut(now, healthSettings.heartbeatTimeout());
                if (timedOut) {
                    deployment.applyHeartbeatTimeout(now, healthSettings.heartbeatTimeout());
                    deployments.save(deployment);
                }
                if (deployment.status().name().equals("HEALTHY") || deployment.status().name().equals("REGISTERED")) {
                    if (!timedOut) {
                        healthy++;
                    } else {
                        unhealthy++;
                    }
                } else if (deployment.status().name().equals("DRAINING")) {
                    draining++;
                } else {
                    unhealthy++;
                }
                deploymentEntries.add(new ModelHealthView.DeploymentHealthEntry(
                        deployment.deploymentKey(),
                        deployment.status(),
                        deployment.acceptsNewWork() && !timedOut,
                        timedOut,
                        deployment.lastHeartbeatAt()
                ));
            }
            entries.add(new ModelHealthView.ModelHealthEntry(
                    definition.modelKey(),
                    definition.status(),
                    definition.acceptsNewWork(),
                    healthy,
                    draining,
                    unhealthy,
                    deploymentEntries
            ));
        }
        return new ModelHealthView(now, List.copyOf(entries));
    }

    /**
     * Compatibility check used by routing (FAZ 12) — model must exist, accept work, and be allowlisted.
     */
    public void assertTenantCompatible(UUID tenantId, String modelKey) {
        ModelDefinition definition = requireModel(modelKey);
        if (!definition.acceptsNewWork()) {
            throw ModelRegistryException.invalidState(
                    "Model " + modelKey + " is not accepting new work (status=" + definition.status() + ")"
            );
        }
        if (!allowlist.isModelAllowed(tenantId, modelKey)) {
            throw ModelRegistryException.modelNotAllowedForTenant(modelKey);
        }
    }

    public boolean isTenantCompatible(UUID tenantId, String modelKey) {
        try {
            assertTenantCompatible(tenantId, modelKey);
            return true;
        } catch (ModelRegistryException ex) {
            return false;
        }
    }

    public ModelDefinitionView getModel(String modelKey) {
        return toView(requireModel(modelKey));
    }

    private ModelDefinition requireModel(String modelKey) {
        return modelDefinitions.findByKey(modelKey)
                .orElseThrow(() -> ModelRegistryException.modelNotFound(modelKey));
    }

    private static Map<String, Object> snapshot(ModelDefinition definition) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("displayName", definition.displayName());
        map.put("contextWindow", definition.contextWindow());
        map.put("maxOutputTokens", definition.maxOutputTokens());
        map.put("status", definition.status().name());
        map.put("priority", definition.priority());
        map.put("version", definition.version());
        return map;
    }

    private static ModelDefinitionView toView(ModelDefinition definition) {
        List<ModelDefinitionView.CapabilityView> caps = definition.capabilities().values().stream()
                .map(c -> new ModelDefinitionView.CapabilityView(
                        c.capability(),
                        c.qualityScore(),
                        c.speedScore(),
                        c.minContextRequired(),
                        c.enabled()
                ))
                .toList();
        return new ModelDefinitionView(
                definition.id(),
                definition.modelKey(),
                definition.displayName(),
                definition.providerType(),
                definition.servedModelId(),
                definition.modelFamily(),
                definition.parameterSize(),
                definition.quantization(),
                definition.contextWindow(),
                definition.maxOutputTokens(),
                definition.supportedLanguages(),
                definition.status(),
                definition.priority(),
                definition.qualityScore(),
                definition.speedScore(),
                definition.createdAt(),
                definition.updatedAt(),
                definition.version(),
                caps
        );
    }

    private ModelDeploymentView toDeploymentView(String modelKey, ModelDeployment deployment, Instant now) {
        boolean timedOut = deployment.isHeartbeatTimedOut(now, healthSettings.heartbeatTimeout());
        return new ModelDeploymentView(
                deployment.id(),
                deployment.modelDefinitionId(),
                modelKey,
                deployment.deploymentKey(),
                deployment.endpoint(),
                deployment.nodeName(),
                deployment.zone(),
                deployment.hardwareType(),
                deployment.gpuType(),
                deployment.gpuCount(),
                deployment.cpuCount(),
                deployment.memoryGb(),
                deployment.maxConcurrency(),
                deployment.status(),
                deployment.lastHeartbeatAt(),
                timedOut,
                deployment.version()
        );
    }
}
