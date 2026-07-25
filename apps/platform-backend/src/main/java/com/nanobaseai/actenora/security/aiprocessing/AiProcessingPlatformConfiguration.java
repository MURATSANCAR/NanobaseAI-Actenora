package com.nanobaseai.actenora.security.aiprocessing;

import com.nanobaseai.actenora.aiprocessing.api.AiProcessingApi;
import com.nanobaseai.actenora.aiprocessing.api.MultiModelRoutingApi;
import com.nanobaseai.actenora.aiprocessing.application.AiJobService;
import com.nanobaseai.actenora.aiprocessing.application.AiProcessingFacade;
import com.nanobaseai.actenora.aiprocessing.application.MultiModelRoutingService;
import com.nanobaseai.actenora.aiprocessing.application.admission.DefaultAdmissionController;
import com.nanobaseai.actenora.aiprocessing.application.port.AdmissionController;
import com.nanobaseai.actenora.aiprocessing.application.port.AiAttemptRepository;
import com.nanobaseai.actenora.aiprocessing.application.port.AiJobRepository;
import com.nanobaseai.actenora.aiprocessing.application.port.AttemptHistoryPort;
import com.nanobaseai.actenora.aiprocessing.application.port.JobScheduler;
import com.nanobaseai.actenora.aiprocessing.application.port.LocalDeploymentCatalogPort;
import com.nanobaseai.actenora.aiprocessing.application.port.ModelCatalogPort;
import com.nanobaseai.actenora.aiprocessing.application.port.ModelQualityMetricsPort;
import com.nanobaseai.actenora.aiprocessing.application.port.ModelRouter;
import com.nanobaseai.actenora.aiprocessing.application.port.RetryQueuePort;
import com.nanobaseai.actenora.aiprocessing.application.port.RoutableCandidate;
import com.nanobaseai.actenora.aiprocessing.application.port.RoutingDecisionStorePort;
import com.nanobaseai.actenora.aiprocessing.application.port.ShadowExecutionStorePort;
import com.nanobaseai.actenora.aiprocessing.application.port.TenantAiPolicyPort;
import com.nanobaseai.actenora.aiprocessing.application.routing.CapabilityModelRouter;
import com.nanobaseai.actenora.aiprocessing.application.scheduling.FairJobScheduler;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiCapability;
import com.nanobaseai.actenora.aiprocessing.domain.routing.MultiModelRouter;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.DefaultModelCatalogBootstrap;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryAiAttemptRepository;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryAiJobRepository;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryModelCatalog;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.InMemoryAttemptHistoryStore;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.InMemoryModelQualityMetricsStore;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.InMemoryRetryQueue;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.InMemoryRoutingDecisionStore;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.InMemoryShadowExecutionStore;
import com.nanobaseai.actenora.modelmanagement.application.DeploymentHealthSettings;
import com.nanobaseai.actenora.modelmanagement.application.ModelDefinitionRepository;
import com.nanobaseai.actenora.modelmanagement.application.ModelDeploymentRepository;
import com.nanobaseai.actenora.modelmanagement.domain.ModelCapability;
import com.nanobaseai.actenora.modelmanagement.domain.ModelDefinition;
import com.nanobaseai.actenora.modelmanagement.domain.ModelDeployment;
import com.nanobaseai.actenora.modelmanagement.domain.ModelStatus;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * FAZ 12 — wire AI job admission/routing/scheduling and FAZ 15 multi-model façade (InMemory).
 */
@Configuration
public class AiProcessingPlatformConfiguration {

    @Bean
    AiJobRepository inMemoryAiJobRepository() {
        return new InMemoryAiJobRepository();
    }

    @Bean
    AiAttemptRepository inMemoryAiAttemptRepository() {
        return new InMemoryAiAttemptRepository();
    }

    @Bean
    @Primary
    ModelCatalogPort preferRegistryModelCatalog(
            ModelDefinitionRepository modelDefinitions,
            ModelDeploymentRepository deployments,
            DeploymentHealthSettings healthSettings,
            InstantClock clock
    ) {
        InMemoryModelCatalog seed = new InMemoryModelCatalog();
        DefaultModelCatalogBootstrap.seed(seed);
        return new PreferRegistryModelCatalog(
                modelDefinitions, deployments, healthSettings, clock, seed);
    }

    @Bean
    ModelRouter capabilityModelRouter(ModelCatalogPort catalog, TenantAiPolicyPort tenantAiPolicy) {
        return new CapabilityModelRouter(catalog, tenantAiPolicy);
    }

    @Bean
    JobScheduler fairJobScheduler(
            AiJobRepository jobs,
            AiAttemptRepository attempts,
            TenantAiPolicyPort tenantAiPolicy,
            ModelRouter modelRouter
    ) {
        return new FairJobScheduler(jobs, attempts, tenantAiPolicy, modelRouter);
    }

    @Bean
    AdmissionController defaultAdmissionController(
            AiJobRepository jobs,
            TenantAiPolicyPort tenantAiPolicy,
            ModelRouter modelRouter,
            JobScheduler jobScheduler
    ) {
        return new DefaultAdmissionController(jobs, tenantAiPolicy, modelRouter, jobScheduler);
    }

    @Bean
    AiJobService aiJobService(
            AdmissionController admissionController,
            AiJobRepository jobs,
            AiAttemptRepository attempts,
            JobScheduler jobScheduler
    ) {
        return new AiJobService(admissionController, jobs, attempts, jobScheduler);
    }

    @Bean
    AiProcessingApi aiProcessingApi(AiJobService aiJobService) {
        return new AiProcessingApi.Default(aiJobService);
    }

    @Bean
    RoutingDecisionStorePort inMemoryRoutingDecisionStore() {
        return new InMemoryRoutingDecisionStore();
    }

    @Bean
    AttemptHistoryPort inMemoryAttemptHistoryStore() {
        return new InMemoryAttemptHistoryStore();
    }

    @Bean
    ShadowExecutionStorePort inMemoryShadowExecutionStore() {
        return new InMemoryShadowExecutionStore();
    }

    @Bean
    ModelQualityMetricsPort inMemoryModelQualityMetricsStore() {
        return new InMemoryModelQualityMetricsStore();
    }

    @Bean
    RetryQueuePort inMemoryRetryQueue() {
        return new InMemoryRetryQueue();
    }

    @Bean
    MultiModelRoutingService multiModelRoutingService(
            LocalDeploymentCatalogPort catalog,
            RoutingDecisionStorePort decisionStore,
            AttemptHistoryPort attemptHistory,
            ShadowExecutionStorePort shadowStore,
            ModelQualityMetricsPort qualityMetrics,
            RetryQueuePort retryQueue,
            Clock clock
    ) {
        return new MultiModelRoutingService(
                new MultiModelRouter(),
                catalog,
                decisionStore,
                attemptHistory,
                shadowStore,
                qualityMetrics,
                retryQueue,
                clock
        );
    }

    @Bean
    MultiModelRoutingApi multiModelRoutingApi(
            MultiModelRoutingService routingService,
            RoutingDecisionStorePort decisionStore,
            ShadowExecutionStorePort shadowStore,
            ModelQualityMetricsPort qualityMetrics
    ) {
        return new AiProcessingFacade(routingService, decisionStore, shadowStore, qualityMetrics);
    }

    /**
     * Projects FAZ 11 registry definitions/deployments into FAZ 12 {@link RoutableCandidate}s.
     * Falls back to bootstrap seed when the registry has no ENABLED models with capabilities.
     */
    public static final class PreferRegistryModelCatalog implements ModelCatalogPort {

        private final ModelDefinitionRepository modelDefinitions;
        private final ModelDeploymentRepository deployments;
        private final DeploymentHealthSettings healthSettings;
        private final InstantClock clock;
        private final InMemoryModelCatalog seed;

        public PreferRegistryModelCatalog(
                ModelDefinitionRepository modelDefinitions,
                ModelDeploymentRepository deployments,
                DeploymentHealthSettings healthSettings,
                InstantClock clock,
                InMemoryModelCatalog seed
        ) {
            this.modelDefinitions = Objects.requireNonNull(modelDefinitions, "modelDefinitions");
            this.deployments = Objects.requireNonNull(deployments, "deployments");
            this.healthSettings = Objects.requireNonNull(healthSettings, "healthSettings");
            this.clock = Objects.requireNonNull(clock, "clock");
            this.seed = Objects.requireNonNull(seed, "seed");
        }

        @Override
        public List<RoutableCandidate> findCandidates(AiCapability capability) {
            List<RoutableCandidate> fromRegistry = projectRegistry();
            if (!fromRegistry.isEmpty()) {
                return fromRegistry.stream()
                        .filter(c -> c.enabledCapabilities().contains(capability))
                        .toList();
            }
            return seed.findCandidates(capability);
        }

        List<RoutableCandidate> projectRegistry() {
            Instant now = clock.now();
            List<RoutableCandidate> candidates = new ArrayList<>();
            for (ModelDefinition definition : modelDefinitions.findAll()) {
                if (definition.status() == ModelStatus.RETIRED) {
                    continue;
                }
                Set<AiCapability> capabilities = enabledCapabilities(definition);
                if (capabilities.isEmpty()) {
                    continue;
                }
                int minContext = minContextRequired(definition);
                for (ModelDeployment deployment : deployments.findByModelDefinitionId(definition.id())) {
                    if (deployment.isHeartbeatTimedOut(now, healthSettings.heartbeatTimeout())) {
                        deployment.applyHeartbeatTimeout(now, healthSettings.heartbeatTimeout());
                        deployments.save(deployment);
                    }
                    candidates.add(new RoutableCandidate(
                            definition.id(),
                            definition.modelKey(),
                            deployment.id(),
                            deployment.deploymentKey(),
                            capabilities,
                            definition.contextWindow(),
                            minContext,
                            definition.supportedLanguages(),
                            deployment.acceptsNewWork(),
                            definition.acceptsNewWork(),
                            deployment.acceptsNewWork(),
                            deployment.maxConcurrency(),
                            0,
                            0,
                            definition.qualityScore(),
                            definition.speedScore(),
                            definition.priority()
                    ));
                }
            }
            return List.copyOf(candidates);
        }

        static Set<AiCapability> enabledCapabilities(ModelDefinition definition) {
            EnumSet<AiCapability> caps = EnumSet.noneOf(AiCapability.class);
            for (ModelCapability capability : definition.capabilities().values()) {
                if (!capability.enabled()) {
                    continue;
                }
                caps.add(AiCapability.valueOf(capability.capability().name()));
            }
            return caps;
        }

        private static int minContextRequired(ModelDefinition definition) {
            int min = 0;
            for (ModelCapability capability : definition.capabilities().values()) {
                if (capability.enabled()) {
                    min = Math.max(min, capability.minContextRequired());
                }
            }
            return min;
        }
    }
}
