package com.nanobaseai.actenora.modelmanagement;

import com.nanobaseai.actenora.modelmanagement.application.ActorPrincipal;
import com.nanobaseai.actenora.modelmanagement.application.ConfigureCapabilityCommand;
import com.nanobaseai.actenora.modelmanagement.application.DeploymentHealthSettings;
import com.nanobaseai.actenora.modelmanagement.application.ModelControlPermission;
import com.nanobaseai.actenora.modelmanagement.application.ModelDefinitionView;
import com.nanobaseai.actenora.modelmanagement.application.ModelHealthView;
import com.nanobaseai.actenora.modelmanagement.application.ModelRegistryService;
import com.nanobaseai.actenora.modelmanagement.application.RegisterDeploymentCommand;
import com.nanobaseai.actenora.modelmanagement.application.RegisterModelCommand;
import com.nanobaseai.actenora.modelmanagement.application.UpdateModelCommand;
import com.nanobaseai.actenora.modelmanagement.domain.DeploymentStatus;
import com.nanobaseai.actenora.modelmanagement.domain.ModelCapabilityType;
import com.nanobaseai.actenora.modelmanagement.domain.ModelRegistryException;
import com.nanobaseai.actenora.modelmanagement.domain.ModelStatus;
import com.nanobaseai.actenora.modelmanagement.infrastructure.ActorPermissionGate;
import com.nanobaseai.actenora.modelmanagement.infrastructure.InMemoryModelDefinitionRepository;
import com.nanobaseai.actenora.modelmanagement.infrastructure.InMemoryModelDeploymentRepository;
import com.nanobaseai.actenora.modelmanagement.infrastructure.InMemoryTenantModelAllowlist;
import com.nanobaseai.actenora.modelmanagement.infrastructure.RecordingModelRegistryAuditPort;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelRegistryServiceTest {

    private final AtomicReference<Instant> now =
            new AtomicReference<>(Instant.parse("2026-07-25T12:00:00Z"));
    private InMemoryModelDefinitionRepository models;
    private InMemoryModelDeploymentRepository deployments;
    private InMemoryTenantModelAllowlist allowlist;
    private RecordingModelRegistryAuditPort audit;
    private ModelRegistryService service;
    private ActorPrincipal admin;
    private ActorPrincipal auditor;

    @BeforeEach
    void setUp() {
        models = new InMemoryModelDefinitionRepository();
        deployments = new InMemoryModelDeploymentRepository();
        allowlist = new InMemoryTenantModelAllowlist();
        audit = new RecordingModelRegistryAuditPort();
        Clock clock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        service = new ModelRegistryService(
                models,
                deployments,
                new ActorPermissionGate(),
                allowlist,
                audit,
                new DeploymentHealthSettings(Duration.ofSeconds(30)),
                new InstantClock(clock)
        );
        admin = ActorPrincipal.operationsAdmin(UUID.randomUUID());
        auditor = ActorPrincipal.of(
                UUID.randomUUID(),
                "AUDITOR",
                Set.of(ModelControlPermission.HEALTH_VIEW)
        );
    }

    @Test
    void duplicateModelKeyIsRejected() {
        registerDefaultModel("local.qwen.27b");
        ModelRegistryException ex = assertThrows(
                ModelRegistryException.class,
                () -> registerDefaultModel("local.qwen.27b")
        );
        assertEquals("DUPLICATE_MODEL_KEY", ex.code());
    }

    @Test
    void invalidContextSizeIsRejected() {
        ModelRegistryException ex = assertThrows(
                ModelRegistryException.class,
                () -> service.registerModel(admin, new RegisterModelCommand(
                        "local.bad",
                        "Bad",
                        "ollama",
                        "bad",
                        "bad",
                        "1B",
                        "q4",
                        1024,
                        2048,
                        List.of("en"),
                        10,
                        0.5,
                        0.5
                ))
        );
        assertEquals("INVALID_CONTEXT_SIZE", ex.code());
    }

    @Test
    void capabilityMinContextCannotExceedModelWindow() {
        registerDefaultModel("local.qwen.27b");
        ModelRegistryException ex = assertThrows(
                ModelRegistryException.class,
                () -> service.configureCapability(
                        admin,
                        "local.qwen.27b",
                        new ConfigureCapabilityCommand(
                                ModelCapabilityType.SUMMARIZATION,
                                0.9,
                                0.5,
                                100_000,
                                true
                        )
                )
        );
        assertEquals("INVALID_CONTEXT_SIZE", ex.code());
    }

    @Test
    void deploymentHeartbeatTimeoutMarksUnhealthy() {
        registerDefaultModel("local.qwen.27b");
        service.registerDeployment(admin, new RegisterDeploymentCommand(
                "local.qwen.27b",
                "node-a.local.qwen.27b",
                "http://127.0.0.1:8081",
                "gpu-node-a",
                "local",
                "gpu",
                "rtx4090",
                1,
                16,
                64,
                4
        ));
        service.heartbeat(admin, "node-a.local.qwen.27b");
        assertEquals(DeploymentStatus.HEALTHY, deployments.findByKey("node-a.local.qwen.27b").orElseThrow().status());

        now.set(now.get().plusSeconds(31));
        ModelHealthView health = service.healthView(admin);
        ModelHealthView.DeploymentHealthEntry entry = health.models().getFirst().deployments().getFirst();
        assertTrue(entry.heartbeatTimedOut());
        assertEquals(DeploymentStatus.UNHEALTHY, entry.status());
        assertFalse(entry.acceptingNewWork());
    }

    @Test
    void drainingStopsNewWorkAndCascadesToDeployments() {
        registerDefaultModel("local.qwen.27b");
        service.registerDeployment(admin, new RegisterDeploymentCommand(
                "local.qwen.27b",
                "node-a.local.qwen.27b",
                "http://127.0.0.1:8081",
                "gpu-node-a",
                "local",
                "gpu",
                "rtx4090",
                1,
                16,
                64,
                4
        ));
        service.heartbeat(admin, "node-a.local.qwen.27b");

        ModelDefinitionView drained = service.drainModel(admin, "local.qwen.27b");
        assertEquals(ModelStatus.DRAINING, drained.status());
        assertFalse(models.findByKey("local.qwen.27b").orElseThrow().acceptsNewWork());
        assertEquals(DeploymentStatus.DRAINING, deployments.findByKey("node-a.local.qwen.27b").orElseThrow().status());
        assertFalse(deployments.findByKey("node-a.local.qwen.27b").orElseThrow().acceptsNewWork());

        // Heartbeat still accepted while draining.
        var hb = service.heartbeat(admin, "node-a.local.qwen.27b");
        assertEquals(DeploymentStatus.DRAINING, hb.status());
    }

    @Test
    void tenantAllowlistCompatibility() {
        registerDefaultModel("local.qwen.27b");
        UUID tenantId = UUID.randomUUID();

        assertFalse(service.isTenantCompatible(tenantId, "local.qwen.27b"));
        ModelRegistryException denied = assertThrows(
                ModelRegistryException.class,
                () -> service.assertTenantCompatible(tenantId, "local.qwen.27b")
        );
        assertEquals("MODEL_NOT_ALLOWED_FOR_TENANT", denied.code());

        allowlist.allow(tenantId, "local.qwen.27b");
        assertTrue(service.isTenantCompatible(tenantId, "local.qwen.27b"));

        service.drainModel(admin, "local.qwen.27b");
        assertFalse(service.isTenantCompatible(tenantId, "local.qwen.27b"));
    }

    @Test
    void permissionChecksEnforceControlPlaneAccess() {
        ModelRegistryException ex = assertThrows(
                ModelRegistryException.class,
                () -> service.registerModel(auditor, defaultCommand("local.denied"))
        );
        assertEquals("PERMISSION_DENIED", ex.code());

        registerDefaultModel("local.qwen.27b");
        assertThrows(
                ModelRegistryException.class,
                () -> service.drainModel(auditor, "local.qwen.27b")
        );

        // HEALTH_VIEW is granted to auditor
        ModelHealthView health = service.healthView(auditor);
        assertEquals(1, health.models().size());
    }

    @Test
    void mutationsAreAudited() {
        registerDefaultModel("local.qwen.27b");
        service.configureCapability(
                admin,
                "local.qwen.27b",
                new ConfigureCapabilityCommand(ModelCapabilityType.FINAL_NOTE, 0.95, 0.4, 4096, true)
        );
        service.disableModel(admin, "local.qwen.27b");
        service.enableModel(admin, "local.qwen.27b");
        service.updateModel(admin, "local.qwen.27b", new UpdateModelCommand(
                "Qwen 27B",
                "ollama",
                "qwen2.5:27b",
                "qwen",
                "27B",
                "q4_k_m",
                32768,
                4096,
                List.of("en", "tr"),
                5,
                0.9,
                0.6
        ));

        assertTrue(audit.entries().stream().anyMatch(e -> e.action().equals("MODEL_REGISTERED")));
        assertTrue(audit.entries().stream().anyMatch(e -> e.action().equals("CAPABILITY_CONFIGURED")));
        assertTrue(audit.entries().stream().anyMatch(e -> e.action().equals("MODEL_DISABLED")));
        assertTrue(audit.entries().stream().anyMatch(e -> e.action().equals("MODEL_ENABLED")));
        assertTrue(audit.entries().stream().anyMatch(e -> e.action().equals("MODEL_UPDATED")));
        assertTrue(audit.entries().stream().anyMatch(e -> e.metadata().containsKey("before")));
    }

    @Test
    void operationsAdminHasAllPermissions() {
        assertEquals(EnumSet.allOf(ModelControlPermission.class), admin.permissions());
    }

    private void registerDefaultModel(String key) {
        service.registerModel(admin, defaultCommand(key));
    }

    private static RegisterModelCommand defaultCommand(String key) {
        return new RegisterModelCommand(
                key,
                "Qwen 27B",
                "ollama",
                "qwen2.5:27b",
                "qwen",
                "27B",
                "q4_k_m",
                32768,
                4096,
                List.of("en", "tr"),
                10,
                0.9,
                0.5
        );
    }
}
