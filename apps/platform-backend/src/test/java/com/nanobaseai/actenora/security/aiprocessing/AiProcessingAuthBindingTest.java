package com.nanobaseai.actenora.security.aiprocessing;

import com.nanobaseai.actenora.aiprocessing.api.AiProcessingApi;
import com.nanobaseai.actenora.aiprocessing.api.MultiModelRoutingApi;
import com.nanobaseai.actenora.aiprocessing.application.execution.AiJobInferenceExecutor;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ExtractionPipelineService;
import com.nanobaseai.actenora.aiprocessing.application.port.LocalDeploymentCatalogPort;
import com.nanobaseai.actenora.aiprocessing.application.port.LocalModelProviderLocator;
import com.nanobaseai.actenora.aiprocessing.application.port.ModelCatalogPort;
import com.nanobaseai.actenora.aiprocessing.application.port.TenantAiPolicyPort;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.infrastructure.adapter.LocalProviderModelRuntimeAdapter;
import com.nanobaseai.actenora.aiprocessing.infrastructure.adapter.Qwen27BModelAdapter;
import com.nanobaseai.actenora.aiprocessing.infrastructure.llm.MockLocalProvider;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryTranscriptSegmentSource;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiCapability;
import com.nanobaseai.actenora.aiprocessing.domain.job.JobPriority;
import com.nanobaseai.actenora.aiprocessing.domain.routing.FallbackStep;
import com.nanobaseai.actenora.aiprocessing.domain.routing.InferenceTaskType;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ModelRole;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.InMemoryLocalDeploymentCatalog;
import com.nanobaseai.actenora.identity.api.IdentityApi;
import com.nanobaseai.actenora.identity.domain.AuthorizationDeniedException;
import com.nanobaseai.actenora.identity.domain.Permission;
import com.nanobaseai.actenora.modelmanagement.application.ActorPrincipal;
import com.nanobaseai.actenora.modelmanagement.application.ConfigureCapabilityCommand;
import com.nanobaseai.actenora.modelmanagement.application.DeploymentHealthSettings;
import com.nanobaseai.actenora.modelmanagement.application.ModelRegistryService;
import com.nanobaseai.actenora.modelmanagement.application.RegisterDeploymentCommand;
import com.nanobaseai.actenora.modelmanagement.application.RegisterModelCommand;
import com.nanobaseai.actenora.modelmanagement.domain.ModelCapabilityType;
import com.nanobaseai.actenora.modelmanagement.infrastructure.ActorPermissionGate;
import com.nanobaseai.actenora.modelmanagement.infrastructure.InMemoryModelDefinitionRepository;
import com.nanobaseai.actenora.modelmanagement.infrastructure.InMemoryModelDeploymentRepository;
import com.nanobaseai.actenora.policy.api.PolicyApi;
import com.nanobaseai.actenora.policy.application.PolicyEvaluationService;
import com.nanobaseai.actenora.policy.domain.ModelAccessPolicy;
import com.nanobaseai.actenora.policy.domain.TenantPolicyOverride;
import com.nanobaseai.actenora.policy.infrastructure.cache.InMemoryPolicyCache;
import com.nanobaseai.actenora.policy.infrastructure.persistence.InMemoryQuotaUsageStore;
import com.nanobaseai.actenora.policy.infrastructure.persistence.InMemoryTenantPolicyRepository;
import com.nanobaseai.actenora.security.model.ModelManagementPlatformConfiguration;
import com.nanobaseai.actenora.security.policy.PolicyPlatformConfiguration;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.security.AuthenticatedPrincipal;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiProcessingAuthBindingTest {

    private static final Instant NOW = Instant.parse("2026-07-25T20:00:00Z");

    private TenantId tenantId;
    private UUID userId;
    private AiProcessingApi aiProcessingApi;
    private MultiModelRoutingApi multiModelRoutingApi;
    private ModelCatalogPort modelCatalog;
    private LocalDeploymentCatalogPort deploymentCatalog;
    private AiProcessingController controller;
    private ModelRegistryService registry;
    private PolicyApi policyApi;
    private InstantClock clock;
    private InMemoryModelDefinitionRepository models;
    private MockLocalProvider provider;
    private AiJobInferenceExecutor executor;
    private InMemoryTranscriptSegmentSource segmentSource;

    @BeforeEach
    void setUp() {
        tenantId = TenantId.random();
        userId = UUID.randomUUID();
        Clock fixed = Clock.fixed(NOW, ZoneOffset.UTC);
        clock = new InstantClock(fixed);

        models = new InMemoryModelDefinitionRepository();
        InMemoryModelDeploymentRepository deployments = new InMemoryModelDeploymentRepository();
        DeploymentHealthSettings health = new DeploymentHealthSettings(Duration.ofSeconds(30));

        policyApi = new PolicyEvaluationService(
                new InMemoryTenantPolicyRepository(),
                new InMemoryPolicyCache(),
                new InMemoryQuotaUsageStore(),
                fixed);
        policyApi.saveOverride(TenantPolicyOverride.builder(tenantId)
                .modelAccess(new ModelAccessPolicy(Set.of("local-final", "local-extract"), true))
                .build());

        var allowlist = (com.nanobaseai.actenora.modelmanagement.application.TenantModelAllowlistPort)
                (tid, modelKey) -> policyApi.isModelAllowed(TenantId.of(tid), modelKey);

        registry = new ModelRegistryService(
                models,
                deployments,
                new ActorPermissionGate(),
                allowlist,
                (actor, action, type, id, meta, at) -> {
                },
                health,
                clock
        );

        deploymentCatalog = new ModelManagementPlatformConfiguration.PreferRegistryLocalDeploymentCatalog(
                models, deployments, health, clock, new InMemoryLocalDeploymentCatalog());
        modelCatalog = new AiProcessingPlatformConfiguration.PreferRegistryModelCatalog(
                models, deployments, health, clock,
                new com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryModelCatalog());

        TenantAiPolicyPort tenantAiPolicy = new PolicyPlatformConfiguration.PolicyBackedTenantAiPolicy(policyApi);

        AiProcessingPlatformConfiguration config = new AiProcessingPlatformConfiguration();
        var jobs = config.inMemoryAiJobRepository();
        var attempts = config.inMemoryAiAttemptRepository();
        var router = config.capabilityModelRouter(modelCatalog, tenantAiPolicy);
        var scheduler = config.fairJobScheduler(jobs, attempts, tenantAiPolicy, router);
        var admission = config.defaultAdmissionController(jobs, tenantAiPolicy, router, scheduler);
        var jobService = config.aiJobService(admission, jobs, attempts, scheduler);
        aiProcessingApi = config.aiProcessingApi(jobService);

        var decisionStore = config.inMemoryRoutingDecisionStore();
        var shadowStore = config.inMemoryShadowExecutionStore();
        var quality = config.inMemoryModelQualityMetricsStore();
        var routingService = config.multiModelRoutingService(
                deploymentCatalog,
                decisionStore,
                config.inMemoryAttemptHistoryStore(),
                shadowStore,
                quality,
                config.inMemoryRetryQueue(),
                fixed
        );
        multiModelRoutingApi = config.multiModelRoutingApi(routingService, decisionStore, shadowStore, quality);

        provider = new MockLocalProvider(2, true, Set.of("local-final", Qwen27BModelAdapter.SERVED_MODEL_ID));
        segmentSource = new InMemoryTranscriptSegmentSource();
        var pipeline = ExtractionPipelineService.create(
                config.inMemoryPromptRegistry(),
                LocalProviderModelRuntimeAdapter.qwen27B(provider, UUID.randomUUID()));
        executor = new AiJobInferenceExecutor(
                jobService,
                LocalModelProviderLocator.single(provider),
                config.promptRegistryInferenceInputResolver(config.inMemoryPromptRegistry()),
                config.registryServedModelResolver(models),
                pipeline,
                segmentSource,
                config.jobRoutingCoordinator(routingService, tenantAiPolicy, new AiRoutingProperties()),
                3,
                600
        );

        IdentityApi identityApi = stubIdentityApi();
        controller = new AiProcessingController(
                aiProcessingApi, multiModelRoutingApi, executor, tenantAiPolicy, identityApi);
    }

    @AfterEach
    void tearDown() {
        TenantSecurityContext.clear();
    }

    @Test
    void registryProjectsIntoCapabilityCatalogAndAdmitsJob() {
        ActorPrincipal admin = ActorPrincipal.operationsAdmin(userId);
        registry.registerModel(admin, registerModel("local-final", 0.95));
        registry.configureCapability(admin, "local-final", new ConfigureCapabilityCommand(
                ModelCapabilityType.FINAL_NOTE, 0.95, 0.7, 0, true));
        registry.registerDeployment(admin, deployment("local-final", "final-a"));
        registry.heartbeat(admin, "final-a");

        assertFalse(modelCatalog.findCandidates(AiCapability.FINAL_NOTE).isEmpty());
        assertEquals("local-final", modelCatalog.findCandidates(AiCapability.FINAL_NOTE).getFirst().modelKey());

        bindPrincipal(Set.of(Permission.MEETING_WRITE.code(), Permission.MEETING_READ.code()));
        AiProcessingController.AdmissionResponse response = controller.submit(
                new AiProcessingController.SubmitAiJobRequest(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "FINAL_NOTE",
                        JobPriority.NORMAL,
                        AiCapability.FINAL_NOTE,
                        "prompt-v1",
                        "schema-v1",
                        "tr",
                        1000,
                        null,
                        UUID.randomUUID()
                )).getBody();

        assertNotNull(response);
        assertTrue(response.admitted());
        assertEquals("local-final", response.job().selectedRoute().modelKey());
        assertTrue(response.job().selectedRoute().reason().contains("healthy_best_model"));
    }

    @Test
    void tenantDisallowedModelIsRejectedOnAdmit() {
        ActorPrincipal admin = ActorPrincipal.operationsAdmin(userId);
        registry.registerModel(admin, registerModel("blocked-model", 0.99));
        registry.configureCapability(admin, "blocked-model", new ConfigureCapabilityCommand(
                ModelCapabilityType.FINAL_NOTE, 0.99, 0.9, 0, true));
        registry.registerDeployment(admin, deployment("blocked-model", "blocked-a"));
        registry.heartbeat(admin, "blocked-a");

        bindPrincipal(Set.of(Permission.MEETING_WRITE.code()));
        assertThrows(
                com.nanobaseai.actenora.aiprocessing.domain.job.AiJobException.class,
                () -> controller.submit(new AiProcessingController.SubmitAiJobRequest(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "FINAL_NOTE",
                        JobPriority.NORMAL,
                        AiCapability.FINAL_NOTE,
                        "prompt-v1",
                        "schema-v1",
                        "tr",
                        1000,
                        null,
                        UUID.randomUUID()
                )));
    }

    @Test
    void unhealthyPrimaryFallsBackWithProvenanceOnMultiModelRoute() {
        ActorPrincipal admin = ActorPrincipal.operationsAdmin(userId);
        registry.registerModel(admin, registerModel("local-final", 0.9));
        registry.configureCapability(admin, "local-final", new ConfigureCapabilityCommand(
                ModelCapabilityType.FINAL_NOTE, 0.9, 0.5, 0, true));
        registry.registerDeployment(admin, deployment("local-final", "final-primary"));
        registry.registerDeployment(admin, new RegisterDeploymentCommand(
                "local-final",
                "final-secondary",
                "http://127.0.0.1:8081/v1",
                "node-b",
                "local",
                "cpu",
                null,
                0,
                4,
                16,
                2));
        registry.heartbeat(admin, "final-primary");
        registry.heartbeat(admin, "final-secondary");
        deploymentCatalog.markHealthy(
                deploymentCatalog.findByRole(ModelRole.QWEN27_FINAL).stream()
                        .filter(d -> d.deploymentKey().equals("final-primary"))
                        .findFirst()
                        .orElseThrow()
                        .deploymentId(),
                false);

        var listed = deploymentCatalog.listLocalDeployments();
        assertEquals(2, listed.size());
        assertTrue(listed.stream().anyMatch(d -> !d.healthy()));
        assertTrue(listed.stream().anyMatch(d -> d.healthy()));

        bindPrincipal(Set.of(Permission.OPERATIONS_MANAGE.code()));
        var decision = controller.route(new AiProcessingController.RouteRequest(
                UUID.randomUUID(),
                InferenceTaskType.FINAL_NOTE,
                false,
                UUID.randomUUID(),
                true,
                null,
                false
        ));

        assertEquals(FallbackStep.SAME_MODEL_OTHER_DEPLOYMENT, decision.fallbackStep());
        assertEquals("local-final", decision.selectedModelKey().orElseThrow());
        assertEquals("final-secondary",
                listed.stream()
                        .filter(d -> d.deploymentId().equals(decision.selectedDeploymentId().orElseThrow()))
                        .findFirst()
                        .orElseThrow()
                        .deploymentKey());
        assertFalse(multiModelRoutingApi.listProvenance(decision.jobId()).isEmpty());
    }

    @Test
    void submittedJobIsExecutedOnLocalProviderThroughHttpSurface() {
        ActorPrincipal admin = ActorPrincipal.operationsAdmin(userId);
        registry.registerModel(admin, registerModel("local-final", 0.95));
        registry.configureCapability(admin, "local-final", new ConfigureCapabilityCommand(
                ModelCapabilityType.FINAL_NOTE, 0.95, 0.7, 0, true));
        registry.registerDeployment(admin, deployment("local-final", "final-a"));
        registry.heartbeat(admin, "final-a");
        provider.setResponse("{\"note\":\"ok\"}");

        bindPrincipal(Set.of(
                Permission.MEETING_WRITE.code(),
                Permission.MEETING_READ.code(),
                Permission.OPERATIONS_MANAGE.code()));
        var submitted = controller.submit(new AiProcessingController.SubmitAiJobRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "FINAL_NOTE",
                JobPriority.NORMAL,
                AiCapability.FINAL_NOTE,
                "prompt-v1",
                "schema-v1",
                "tr",
                1000,
                null,
                UUID.randomUUID()
        )).getBody();
        assertNotNull(submitted);

        var execution = controller.executeNext().getBody();

        assertNotNull(execution);
        assertTrue(execution.succeeded());
        assertEquals(submitted.job().id(), execution.jobId());
        assertEquals("SUCCEEDED", execution.jobStatus());
        assertEquals("SUCCEEDED", controller.find(submitted.job().id()).status());
    }

    @Test
    void extractionJobRunsPipelineThroughExecuteNext() {
        ActorPrincipal admin = ActorPrincipal.operationsAdmin(userId);
        registry.registerModel(admin, registerModel("local-extract", 0.9));
        registry.configureCapability(admin, "local-extract", new ConfigureCapabilityCommand(
                ModelCapabilityType.TRANSCRIPT_EXTRACTION, 0.9, 0.8, 0, true));
        registry.registerDeployment(admin, deployment("local-extract", "extract-a"));
        registry.heartbeat(admin, "extract-a");

        UUID transcriptId = UUID.randomUUID();
        segmentSource.put(tenantId, transcriptId, List.of(
                new SegmentInput("seg-1", 0, "Alice", 0, 1000, "We decided to ship Friday.", true)
        ));
        provider.setResponse("""
                {
                  "topics": [{"text":"Delivery","evidenceSegmentIds":["seg-1"],"confidence":0.9}],
                  "decisions": [{"text":"Ship Friday","evidenceSegmentIds":["seg-1"],"confidence":0.9}],
                  "actionItems": [],
                  "risks": [],
                  "openQuestions": [],
                  "commitments": [],
                  "qualityFlags": [],
                  "evidenceSegmentIds": ["seg-1"],
                  "confidence": 0.9
                }
                """);

        policyApi.saveOverride(TenantPolicyOverride.builder(tenantId)
                .modelAccess(new ModelAccessPolicy(Set.of("local-final", "local-extract"), true))
                .build());

        bindPrincipal(Set.of(
                Permission.MEETING_WRITE.code(),
                Permission.MEETING_READ.code(),
                Permission.OPERATIONS_MANAGE.code()));
        var submitted = controller.submit(new AiProcessingController.SubmitAiJobRequest(
                UUID.randomUUID(),
                transcriptId,
                "CHUNK_EXTRACTION",
                JobPriority.NORMAL,
                AiCapability.TRANSCRIPT_EXTRACTION,
                "prompt-v1",
                "schema-v1",
                "tr",
                1000,
                null,
                UUID.randomUUID()
        )).getBody();
        assertNotNull(submitted);

        var execution = controller.executeNext().getBody();
        assertNotNull(execution);
        assertTrue(execution.succeeded());
        assertEquals("SUCCEEDED", execution.jobStatus());
        assertTrue(execution.latencyMs() >= 0);
    }

    @Test
    void executedJobExposesRoutingProvenanceAndQualityMetrics() {
        ActorPrincipal admin = ActorPrincipal.operationsAdmin(userId);
        registry.registerModel(admin, registerModel("local-final", 0.95));
        registry.configureCapability(admin, "local-final", new ConfigureCapabilityCommand(
                ModelCapabilityType.FINAL_NOTE, 0.95, 0.7, 0, true));
        registry.registerDeployment(admin, deployment("local-final", "final-a"));
        registry.heartbeat(admin, "final-a");
        provider.setResponse("{\"note\":\"ok\"}");

        bindPrincipal(Set.of(
                Permission.MEETING_WRITE.code(),
                Permission.MEETING_READ.code(),
                Permission.OPERATIONS_MANAGE.code()));
        var submitted = controller.submit(new AiProcessingController.SubmitAiJobRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "FINAL_NOTE",
                JobPriority.NORMAL,
                AiCapability.FINAL_NOTE,
                "prompt-v1",
                "schema-v1",
                "tr",
                1000,
                null,
                UUID.randomUUID()
        )).getBody();
        assertNotNull(submitted);

        var execution = controller.executeNext().getBody();
        assertNotNull(execution);
        assertTrue(execution.succeeded());

        UUID jobId = submitted.job().id();
        var decisions = controller.routingDecisions(jobId);
        assertEquals(1, decisions.size());
        assertEquals(FallbackStep.PRIMARY, decisions.getFirst().fallbackStep());
        assertEquals("local-final", decisions.getFirst().selectedModelKey().orElseThrow());

        var metrics = controller.modelQuality();
        assertEquals(1, metrics.size());
        assertEquals(1, metrics.getFirst().successCount());
        assertEquals(204, controller.routingShadow(jobId).getStatusCode().value());
    }

    @Test
    void routingProvenanceIsDeniedForForeignTenantJob() {
        bindPrincipal(Set.of(Permission.OPERATIONS_MANAGE.code()));
        assertThrows(
                com.nanobaseai.actenora.aiprocessing.domain.job.AiJobException.class,
                () -> controller.routingDecisions(UUID.randomUUID()));
    }

    @Test
    void executeNextReturnsNoContentOnEmptyQueue() {
        bindPrincipal(Set.of(Permission.OPERATIONS_MANAGE.code()));
        assertEquals(204, controller.executeNext().getStatusCode().value());
    }

    @Test
    void submitDeniedWithoutPermission() {
        bindPrincipal(Set.of(Permission.MEETING_READ.code()));
        assertThrows(
                AuthorizationDeniedException.class,
                () -> controller.submit(new AiProcessingController.SubmitAiJobRequest(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "FINAL_NOTE",
                        JobPriority.NORMAL,
                        AiCapability.FINAL_NOTE,
                        "prompt-v1",
                        "schema-v1",
                        "tr",
                        100,
                        null,
                        UUID.randomUUID()
                )));
    }

    private void bindPrincipal(Set<String> permissions) {
        TenantSecurityContext.set(new AuthenticatedPrincipal(
                tenantId,
                userId,
                "oid",
                "ops@example.com",
                "Ops",
                Set.of("OPERATIONS"),
                permissions,
                false
        ));
    }

    private static RegisterModelCommand registerModel(String key, double quality) {
        return new RegisterModelCommand(
                key, key, "ollama", key, "qwen", "7B", "q4",
                8192, 1024, List.of("en", "tr"), 10, quality, 0.6
        );
    }

    private static RegisterDeploymentCommand deployment(String modelKey, String deploymentKey) {
        return new RegisterDeploymentCommand(
                modelKey,
                deploymentKey,
                "http://127.0.0.1:8080/v1",
                "node-a",
                "local",
                "cpu",
                null,
                0,
                4,
                16,
                2
        );
    }

    private IdentityApi stubIdentityApi() {
        return new IdentityApi() {
            @Override
            public AuthenticatedPrincipal resolvePrincipal(
                    TenantId tenantId,
                    com.nanobaseai.actenora.sharedkernel.security.IdentityClaims claims) {
                throw new UnsupportedOperationException();
            }

            @Override
            public com.nanobaseai.actenora.identity.api.UserView currentUser(AuthenticatedPrincipal principal) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.List<com.nanobaseai.actenora.identity.api.UserView> listUsers(TenantId tenantId) {
                return List.of();
            }

            @Override
            public com.nanobaseai.actenora.identity.api.UserView grantRole(
                    TenantId tenantId,
                    UUID userId,
                    com.nanobaseai.actenora.identity.domain.SystemRole role,
                    long expectedVersion,
                    UUID actorUserId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public com.nanobaseai.actenora.identity.api.UserView revokeRole(
                    TenantId tenantId,
                    UUID userId,
                    com.nanobaseai.actenora.identity.domain.SystemRole role,
                    long expectedVersion,
                    UUID actorUserId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void requirePermission(AuthenticatedPrincipal principal, Permission permission) {
                if (!principal.permissions().contains(permission.code())) {
                    throw new AuthorizationDeniedException(principal.userId(), permission.code());
                }
            }

            @Override
            public java.util.Optional<com.nanobaseai.actenora.identity.api.UserView> findByEntraObjectId(
                    String entraObjectId) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.Optional<com.nanobaseai.actenora.identity.api.UserView> findById(
                    TenantId tenantId, UUID userId) {
                return java.util.Optional.empty();
            }
        };
    }
}
