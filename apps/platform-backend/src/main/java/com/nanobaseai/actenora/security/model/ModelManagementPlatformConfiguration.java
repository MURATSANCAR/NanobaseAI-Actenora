package com.nanobaseai.actenora.security.model;

import com.nanobaseai.actenora.aiprocessing.application.port.LocalDeploymentCatalogPort;
import com.nanobaseai.actenora.aiprocessing.domain.routing.LocalDeploymentRef;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ModelRole;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.DefaultModelRoleBootstrap;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.InMemoryLocalDeploymentCatalog;
import com.nanobaseai.actenora.audit.api.AuditApi;
import com.nanobaseai.actenora.modelmanagement.application.DeploymentHealthSettings;
import com.nanobaseai.actenora.modelmanagement.application.ModelDefinitionRepository;
import com.nanobaseai.actenora.modelmanagement.application.ModelDeploymentRepository;
import com.nanobaseai.actenora.modelmanagement.application.ModelRegistryAuditPort;
import com.nanobaseai.actenora.modelmanagement.domain.ModelCapability;
import com.nanobaseai.actenora.modelmanagement.domain.ModelCapabilityType;
import com.nanobaseai.actenora.modelmanagement.domain.ModelDefinition;
import com.nanobaseai.actenora.modelmanagement.domain.ModelDeployment;
import com.nanobaseai.actenora.modelmanagement.domain.ModelStatus;
import com.nanobaseai.actenora.security.aiprocessing.LocalProviderProperties;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * FAZ 11 — bind model-management to AuditApi and project registry into AI routing catalog.
 */
@Configuration
public class ModelManagementPlatformConfiguration {

    @Bean
    @Primary
    ModelRegistryAuditPort auditBackedModelRegistryAuditPort(AuditApi auditApi) {
        return (actorUserId, action, resourceType, resourceId, metadata, occurredAt) -> {
            UUID tenantId = TenantSecurityContext.current()
                    .map(p -> p.tenantId().value())
                    .orElseGet(() -> UUID.fromString("00000000-0000-0000-0000-000000000001"));
            UUID resourceUuid = UUID.nameUUIDFromBytes(resourceId.getBytes(StandardCharsets.UTF_8));
            auditApi.append(
                    tenantId,
                    actorUserId,
                    action,
                    resourceType,
                    resourceUuid,
                    metadata == null ? Map.of() : metadata,
                    occurredAt
            );
        };
    }

    @Bean
    @Primary
    LocalDeploymentCatalogPort modelRegistryLocalDeploymentCatalog(
            ModelDefinitionRepository modelDefinitions,
            ModelDeploymentRepository deployments,
            DeploymentHealthSettings healthSettings,
            InstantClock clock,
            LocalProviderProperties providerProperties
    ) {
        InMemoryLocalDeploymentCatalog seed = new InMemoryLocalDeploymentCatalog();
        boolean realFast = providerProperties.hasFastExtractionServedModelId();
        DefaultModelRoleBootstrap.seed(seed, realFast);
        return new PreferRegistryLocalDeploymentCatalog(
                modelDefinitions, deployments, healthSettings, clock, seed);
    }

    /**
     * Prefers registry deployments (including unhealthy for failover); falls back to bootstrap seed catalog.
     */
    public static final class PreferRegistryLocalDeploymentCatalog implements LocalDeploymentCatalogPort {

        private final ModelDefinitionRepository modelDefinitions;
        private final ModelDeploymentRepository deployments;
        private final DeploymentHealthSettings healthSettings;
        private final InstantClock clock;
        private final InMemoryLocalDeploymentCatalog seed;

        public PreferRegistryLocalDeploymentCatalog(
                ModelDefinitionRepository modelDefinitions,
                ModelDeploymentRepository deployments,
                DeploymentHealthSettings healthSettings,
                InstantClock clock,
                InMemoryLocalDeploymentCatalog seed
        ) {
            this.modelDefinitions = modelDefinitions;
            this.deployments = deployments;
            this.healthSettings = healthSettings;
            this.clock = clock;
            this.seed = seed;
        }

        @Override
        public List<LocalDeploymentRef> listLocalDeployments() {
            List<LocalDeploymentRef> fromRegistry = projectRegistry();
            if (!fromRegistry.isEmpty()) {
                return fromRegistry;
            }
            return seed.listLocalDeployments();
        }

        @Override
        public Optional<LocalDeploymentRef> findByDeploymentId(UUID deploymentId) {
            return listLocalDeployments().stream()
                    .filter(d -> d.deploymentId().equals(deploymentId))
                    .findFirst()
                    .or(() -> seed.findByDeploymentId(deploymentId));
        }

        @Override
        public List<LocalDeploymentRef> findByRole(ModelRole role) {
            return listLocalDeployments().stream().filter(d -> d.role() == role).toList();
        }

        @Override
        public void upsert(LocalDeploymentRef deployment) {
            seed.upsert(deployment);
        }

        @Override
        public void markHealthy(UUID deploymentId, boolean healthy) {
            Optional<ModelDeployment> registry = deployments.findAll().stream()
                    .filter(d -> d.id().equals(deploymentId))
                    .findFirst();
            if (registry.isPresent()) {
                ModelDeployment deployment = registry.get();
                if (healthy) {
                    deployment.heartbeat(clock.now());
                } else {
                    deployment.applyHeartbeatTimeout(
                            clock.now().plus(healthSettings.heartbeatTimeout()).plusSeconds(1),
                            healthSettings.heartbeatTimeout());
                }
                deployments.save(deployment);
                return;
            }
            seed.markHealthy(deploymentId, healthy);
        }

        private List<LocalDeploymentRef> projectRegistry() {
            Instant now = clock.now();
            List<LocalDeploymentRef> refs = new ArrayList<>();
            for (ModelDefinition definition : modelDefinitions.findAll()) {
                if (definition.status() != ModelStatus.ENABLED) {
                    continue;
                }
                List<ModelRole> roles = resolveRoles(definition);
                for (ModelDeployment deployment : deployments.findByModelDefinitionId(definition.id())) {
                    if (deployment.isHeartbeatTimedOut(now, healthSettings.heartbeatTimeout())) {
                        deployment.applyHeartbeatTimeout(now, healthSettings.heartbeatTimeout());
                        deployments.save(deployment);
                    }
                    // Keep unhealthy / draining deployments so FAZ 15 can fall back
                    // SAME_MODEL_OTHER_DEPLOYMENT with accurate provenance.
                    for (ModelRole role : roles) {
                        refs.add(new LocalDeploymentRef(
                                deployment.id(),
                                definition.id(),
                                definition.modelKey(),
                                deployment.deploymentKey(),
                                role,
                                definition.qualityScore(),
                                deployment.acceptsNewWork(),
                                false,
                                definition.priority()
                        ));
                    }
                }
            }
            refs.sort(Comparator.comparingInt(LocalDeploymentRef::priority)
                    .thenComparing(LocalDeploymentRef::deploymentKey)
                    .thenComparing(d -> d.role().name()));
            return List.copyOf(refs);
        }

        /**
         * One physical deployment can serve multiple pipeline roles when its capability set covers them.
         * Preferring a single role (e.g. FINAL_NOTE over TRANSCRIPT_EXTRACTION) left CHUNK_EXTRACTION
         * with an empty FAST_EXTRACTION catalog and forced retry-queue.
         */
        static List<ModelRole> resolveRoles(ModelDefinition definition) {
            List<ModelRole> roles = new ArrayList<>();
            if (capabilityEnabled(definition, ModelCapabilityType.TRANSCRIPT_EXTRACTION)
                    || capabilityEnabled(definition, ModelCapabilityType.DECISION_EXTRACTION)
                    || capabilityEnabled(definition, ModelCapabilityType.ACTION_EXTRACTION)
                    || capabilityEnabled(definition, ModelCapabilityType.RISK_EXTRACTION)) {
                roles.add(ModelRole.FAST_EXTRACTION);
            }
            if (capabilityEnabled(definition, ModelCapabilityType.FINAL_NOTE)
                    || capabilityEnabled(definition, ModelCapabilityType.SUMMARIZATION)) {
                roles.add(ModelRole.QWEN27_FINAL);
            }
            if (capabilityEnabled(definition, ModelCapabilityType.VALIDATION)) {
                roles.add(ModelRole.VALIDATION);
            }
            if (roles.isEmpty()) {
                roles.add(ModelRole.QWEN27_FINAL);
            }
            return List.copyOf(roles);
        }

        /** @deprecated use {@link #resolveRoles(ModelDefinition)} */
        static ModelRole resolveRole(ModelDefinition definition) {
            return resolveRoles(definition).getFirst();
        }

        private static boolean capabilityEnabled(ModelDefinition definition, ModelCapabilityType type) {
            return definition.capability(type).map(ModelCapability::enabled).orElse(false);
        }
    }
}
