package com.nanobaseai.actenora.security.model;

import com.nanobaseai.actenora.aiprocessing.application.port.LocalDeploymentCatalogPort;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ModelRole;
import com.nanobaseai.actenora.audit.api.AuditApi;
import com.nanobaseai.actenora.audit.application.AuditAppendService;
import com.nanobaseai.actenora.audit.infrastructure.AuditApiAdapter;
import com.nanobaseai.actenora.audit.infrastructure.InMemoryAuditEntryStore;
import com.nanobaseai.actenora.modelmanagement.api.ModelControlPlaneController;
import com.nanobaseai.actenora.modelmanagement.api.ModelManagementApi;
import com.nanobaseai.actenora.modelmanagement.application.ActorPrincipal;
import com.nanobaseai.actenora.modelmanagement.application.ConfigureCapabilityCommand;
import com.nanobaseai.actenora.modelmanagement.application.DeploymentHealthSettings;
import com.nanobaseai.actenora.modelmanagement.application.ModelRegistryService;
import com.nanobaseai.actenora.modelmanagement.application.RegisterDeploymentCommand;
import com.nanobaseai.actenora.modelmanagement.application.RegisterModelCommand;
import com.nanobaseai.actenora.modelmanagement.domain.ModelCapabilityType;
import com.nanobaseai.actenora.modelmanagement.domain.ModelRegistryException;
import com.nanobaseai.actenora.modelmanagement.domain.ModelStatus;
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
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.security.AuthenticatedPrincipal;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelAuthBindingTest {

    private static final Instant NOW = Instant.parse("2026-07-25T19:00:00Z");

    private TenantId tenantId;
    private UUID userId;
    private ModelRegistryService service;
    private ModelControlPlaneController controller;
    private AuditApi auditApi;
    private InMemoryModelDefinitionRepository models;
    private InMemoryModelDeploymentRepository deployments;
    private PolicyApi policyApi;
    private LocalDeploymentCatalogPort catalog;

    @BeforeEach
    void setUp() {
        tenantId = TenantId.random();
        userId = UUID.randomUUID();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        InstantClock instantClock = new InstantClock(clock);
        models = new InMemoryModelDefinitionRepository();
        deployments = new InMemoryModelDeploymentRepository();
        auditApi = new AuditApiAdapter(new AuditAppendService(new InMemoryAuditEntryStore()));
        ModelRegistryAuditPortAdapter audit = new ModelRegistryAuditPortAdapter(auditApi);

        policyApi = new PolicyEvaluationService(
                new InMemoryTenantPolicyRepository(),
                new InMemoryPolicyCache(),
                new InMemoryQuotaUsageStore(),
                clock);
        var allowlist = (com.nanobaseai.actenora.modelmanagement.application.TenantModelAllowlistPort)
                (tid, modelKey) -> policyApi.isModelAllowed(TenantId.of(tid), modelKey);

        DeploymentHealthSettings health = new DeploymentHealthSettings(Duration.ofSeconds(30));
        service = new ModelRegistryService(
                models,
                deployments,
                new ActorPermissionGate(),
                allowlist,
                audit,
                health,
                instantClock
        );
        controller = new ModelControlPlaneController(new ModelManagementApi.Default(service));
        catalog = new ModelManagementPlatformConfiguration.PreferRegistryLocalDeploymentCatalog(
                models, deployments, health, instantClock,
                new com.nanobaseai.actenora.aiprocessing.infrastructure.routing.InMemoryLocalDeploymentCatalog());
    }

    @AfterEach
    void tearDown() {
        TenantSecurityContext.clear();
    }

    @Test
    void registerRequiresModelControlPermission() {
        bindPrincipal(Set.of());
        assertThrows(ModelRegistryException.class, () -> controller.register(sampleRegisterBody("local-qwen")));
    }

    @Test
    void registerAndAuditWithModelControl() {
        bindPrincipal(Set.of("MODEL_CONTROL"));
        var view = controller.register(sampleRegisterBody("local-qwen"));
        assertEquals("local-qwen", view.modelKey());
        assertEquals(ModelStatus.ENABLED, view.status());
        UUID resourceId = UUID.nameUUIDFromBytes("local-qwen".getBytes(StandardCharsets.UTF_8));
        assertTrue(auditApi.timeline(tenantId.value(), resourceId).stream()
                .anyMatch(e -> "MODEL_REGISTERED".equals(e.action())));
    }

    @Test
    void cloudProviderRejected() {
        bindPrincipal(Set.of("MODEL_CONTROL"));
        ModelRegistryException ex = assertThrows(
                ModelRegistryException.class,
                () -> controller.register(new ModelControlPlaneController.RegisterModelRequest(
                        "gpt",
                        "GPT",
                        "openai",
                        "gpt-4",
                        "gpt",
                        "large",
                        null,
                        8192,
                        1024,
                        List.of("en"),
                        10,
                        0.9,
                        0.5)));
        assertEquals("CLOUD_PROVIDER_REJECTED", ex.code());
    }

    @Test
    void tenantAllowlistBoundToPolicy() {
        bindPrincipal(Set.of("MODEL_CONTROL"));
        controller.register(sampleRegisterBody("qwen-local"));
        policyApi.saveOverride(TenantPolicyOverride.builder(tenantId)
                .modelAccess(new ModelAccessPolicy(Set.of("qwen-local"), false))
                .build());
        service.assertTenantCompatible(tenantId.value(), "qwen-local");
        assertThrows(
                ModelRegistryException.class,
                () -> service.assertTenantCompatible(tenantId.value(), "other-model"));
    }

    @Test
    void registryProjectsIntoRoutingCatalog() {
        ActorPrincipal admin = ActorPrincipal.operationsAdmin(userId);
        service.registerModel(admin, toCommand(sampleRegisterBody("local-final")));
        service.configureCapability(admin, "local-final", new ConfigureCapabilityCommand(
                ModelCapabilityType.FINAL_NOTE, 0.9, 0.5, 0, true));
        service.registerDeployment(admin, new RegisterDeploymentCommand(
                "local-final",
                "final-primary",
                "http://127.0.0.1:8080/v1",
                "node-a",
                "local",
                "cpu",
                null,
                0,
                4,
                16,
                2));
        service.heartbeat(admin, "final-primary");

        var listed = catalog.listLocalDeployments();
        assertFalse(listed.isEmpty());
        assertEquals("local-final", listed.getFirst().modelKey());
        assertEquals(ModelRole.QWEN27_FINAL, listed.getFirst().role());
        assertTrue(listed.getFirst().healthy());
    }

    private void bindPrincipal(Set<String> permissions) {
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                tenantId,
                userId,
                "oid",
                "ops@example.com",
                "Ops",
                Set.of("OPERATIONS"),
                permissions,
                false
        );
        TenantSecurityContext.set(principal);
    }

    private static ModelControlPlaneController.RegisterModelRequest sampleRegisterBody(String key) {
        return new ModelControlPlaneController.RegisterModelRequest(
                key,
                key,
                "ollama",
                key,
                "qwen",
                "7B",
                "q4",
                8192,
                1024,
                List.of("en", "tr"),
                10,
                0.9,
                0.6
        );
    }

    private static RegisterModelCommand toCommand(ModelControlPlaneController.RegisterModelRequest body) {
        return new RegisterModelCommand(
                body.modelKey(),
                body.displayName(),
                body.providerType(),
                body.servedModelId(),
                body.modelFamily(),
                body.parameterSize(),
                body.quantization(),
                body.contextWindow(),
                body.maxOutputTokens(),
                body.supportedLanguages(),
                body.priority(),
                body.qualityScore(),
                body.speedScore()
        );
    }

    private static final class ModelRegistryAuditPortAdapter
            implements com.nanobaseai.actenora.modelmanagement.application.ModelRegistryAuditPort {
        private final AuditApi auditApi;

        private ModelRegistryAuditPortAdapter(AuditApi auditApi) {
            this.auditApi = auditApi;
        }

        @Override
        public void append(
                String actorUserId,
                String action,
                String resourceType,
                String resourceId,
                java.util.Map<String, Object> metadata,
                Instant occurredAt
        ) {
            UUID tenant = TenantSecurityContext.current()
                    .map(p -> p.tenantId().value())
                    .orElse(UUID.fromString("00000000-0000-0000-0000-000000000001"));
            auditApi.append(
                    tenant,
                    actorUserId,
                    action,
                    resourceType,
                    UUID.nameUUIDFromBytes(resourceId.getBytes(StandardCharsets.UTF_8)),
                    metadata == null ? java.util.Map.of() : metadata,
                    occurredAt
            );
        }
    }
}
