package com.nanobaseai.actenora.security.aiprocessing;

import com.nanobaseai.actenora.aiprocessing.api.AiProcessingApi;
import com.nanobaseai.actenora.aiprocessing.api.MultiModelRoutingApi;
import com.nanobaseai.actenora.aiprocessing.application.execution.AiJobInferenceExecutor;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ExtractionPipelineService;
import com.nanobaseai.actenora.aiprocessing.application.port.LocalModelProviderLocator;
import com.nanobaseai.actenora.aiprocessing.application.port.MeetingNoteHandoffPort;
import com.nanobaseai.actenora.aiprocessing.application.port.ModelCatalogPort;
import com.nanobaseai.actenora.aiprocessing.application.port.TenantAiPolicyPort;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiCapability;
import com.nanobaseai.actenora.aiprocessing.domain.job.JobPriority;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.infrastructure.adapter.LocalProviderModelRuntimeAdapter;
import com.nanobaseai.actenora.aiprocessing.infrastructure.adapter.Qwen27BModelAdapter;
import com.nanobaseai.actenora.aiprocessing.infrastructure.llm.MockLocalProvider;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryTranscriptSegmentSource;
import com.nanobaseai.actenora.identity.api.IdentityApi;
import com.nanobaseai.actenora.identity.domain.AuthorizationDeniedException;
import com.nanobaseai.actenora.identity.api.Permission;
import com.nanobaseai.actenora.meetingintelligence.api.EvidenceValidationApi;
import com.nanobaseai.actenora.meetingintelligence.api.MeetingIntelligenceApi;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteDetailResponse;
import com.nanobaseai.actenora.meetingintelligence.application.validation.EvidenceValidationService;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.QualityGateOutcome;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.RecordingMeetingIntelligenceAuditPort;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.validation.RecordingValidationAuditPort;
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
import com.nanobaseai.actenora.security.meetingintelligence.EvidenceValidationAuthController;
import com.nanobaseai.actenora.security.meetingintelligence.MeetingIntelligenceAuthController;
import com.nanobaseai.actenora.security.meetingintelligence.MeetingIntelligenceHandoffAdapter;
import com.nanobaseai.actenora.security.meetingintelligence.MeetingIntelligencePlatformConfiguration;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FAZ 16/17 — Meeting Intelligence auth binding + quality-gated AI extraction handoff.
 */
class MeetingIntelligenceAuthBindingTest {

    private static final Instant NOW = Instant.parse("2026-07-25T21:00:00Z");

    private TenantId tenantId;
    private UUID userId;
    private MeetingIntelligenceApi meetingIntelligenceApi;
    private EvidenceValidationApi evidenceValidationApi;
    private MeetingIntelligenceAuthController miController;
    private EvidenceValidationAuthController validationController;
    private AiProcessingController aiController;
    private RecordingMeetingIntelligenceAuditPort audit;
    private ModelRegistryService registry;
    private PolicyApi policyApi;
    private MockLocalProvider provider;
    private InMemoryTranscriptSegmentSource segmentSource;

    @BeforeEach
    void setUp() {
        tenantId = TenantId.random();
        userId = UUID.randomUUID();
        Clock fixed = Clock.fixed(NOW, ZoneOffset.UTC);
        InstantClock clock = new InstantClock(fixed);

        InMemoryModelDefinitionRepository models = new InMemoryModelDefinitionRepository();
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

        ModelCatalogPort modelCatalog = new AiProcessingPlatformConfiguration.PreferRegistryModelCatalog(
                models, deployments, health, clock,
                new com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryModelCatalog());
        TenantAiPolicyPort tenantAiPolicy = new PolicyPlatformConfiguration.PolicyBackedTenantAiPolicy(policyApi);

        MeetingIntelligencePlatformConfiguration miConfig = new MeetingIntelligencePlatformConfiguration();
        audit = new RecordingMeetingIntelligenceAuditPort();
        var notes = miConfig.inMemoryMeetingNoteRepository();
        var versions = miConfig.inMemoryMeetingNoteVersionRepository();
        var decisions = miConfig.inMemoryDecisionRepository();
        var actionItems = miConfig.inMemoryActionItemRepository();
        var risks = miConfig.inMemoryRiskRepository();
        var commitments = miConfig.inMemoryCommitmentRepository();
        var openQuestions = miConfig.inMemoryOpenQuestionRepository();
        var evidence = miConfig.inMemoryEvidenceLinkRepository();
        var qualityFlags = miConfig.inMemoryQualityFlagRepository();
        var clockPort = miConfig.meetingIntelligenceClockPort();
        var tenantPort = miConfig.meetingIntelligenceTenantContextPort();
        var mapping = miConfig.mapAiCandidatesToNoteService(
                notes, versions, decisions, actionItems, risks, commitments, openQuestions, evidence, qualityFlags, clockPort);
        var miService = miConfig.meetingIntelligenceApplicationService(
                tenantPort, clockPort, mapping, notes, versions, decisions, actionItems,
                risks, commitments, openQuestions, evidence, qualityFlags);
        meetingIntelligenceApi = miConfig.meetingIntelligenceApi(miService);
        segmentSource = new InMemoryTranscriptSegmentSource();
        var runs = miConfig.inMemoryValidationRunRepository();
        var reviews = miConfig.inMemoryManualReviewCaseRepository();
        var gatePolicy = miConfig.inMemoryQualityGatePolicyPort();
        RecordingValidationAuditPort validationAudit = new RecordingValidationAuditPort();
        EvidenceValidationService validationService = new EvidenceValidationService(
                runs, reviews, gatePolicy, validationAudit, Clock.fixed(NOW, ZoneOffset.UTC));
        evidenceValidationApi = miConfig.evidenceValidationApi(validationService);
        MeetingNoteHandoffPort handoff = new MeetingIntelligenceHandoffAdapter(
                meetingIntelligenceApi, evidenceValidationApi, segmentSource, audit);

        AiProcessingPlatformConfiguration aiConfig = new AiProcessingPlatformConfiguration();
        var jobs = aiConfig.inMemoryAiJobRepository();
        var attempts = aiConfig.inMemoryAiAttemptRepository();
        var router = aiConfig.capabilityModelRouter(modelCatalog, tenantAiPolicy);
        var scheduler = aiConfig.fairJobScheduler(jobs, attempts, tenantAiPolicy, router, new LocalProviderProperties());
        var admission = aiConfig.defaultAdmissionController(jobs, tenantAiPolicy, router, scheduler);
        var jobService = aiConfig.aiJobService(
                admission, jobs, attempts, scheduler, new com.nanobaseai.actenora.sharedkernel.coordination.InMemoryJobProgressCache());
        AiProcessingApi aiProcessingApi = aiConfig.aiProcessingApi(jobService);

        var decisionStore = aiConfig.inMemoryRoutingDecisionStore();
        var shadowStore = aiConfig.inMemoryShadowExecutionStore();
        var quality = aiConfig.inMemoryModelQualityMetricsStore();
        var routingService = aiConfig.multiModelRoutingService(
                new com.nanobaseai.actenora.aiprocessing.infrastructure.routing.InMemoryLocalDeploymentCatalog(),
                decisionStore,
                aiConfig.inMemoryAttemptHistoryStore(),
                shadowStore,
                quality,
                aiConfig.inMemoryRetryQueue(),
                fixed
        );
        MultiModelRoutingApi multiModelRoutingApi =
                aiConfig.multiModelRoutingApi(routingService, decisionStore, shadowStore, quality);

        provider = new MockLocalProvider(2, true, Set.of("local-final", Qwen27BModelAdapter.SERVED_MODEL_ID));
        var pipeline = ExtractionPipelineService.create(
                aiConfig.inMemoryPromptRegistry(),
                LocalProviderModelRuntimeAdapter.qwen27B(provider, UUID.randomUUID()));
        AiJobInferenceExecutor executor = new AiJobInferenceExecutor(
                jobService,
                LocalModelProviderLocator.single(provider),
                aiConfig.promptRegistryInferenceInputResolver(aiConfig.inMemoryPromptRegistry()),
                aiConfig.registryServedModelResolver(models, new com.nanobaseai.actenora.security.aiprocessing.LocalProviderProperties()),
                pipeline,
                segmentSource,
                null,
                handoff,
                3,
                600
        );

        IdentityApi identityApi = stubIdentityApi();
        miController = new MeetingIntelligenceAuthController(meetingIntelligenceApi, identityApi);
        validationController = new EvidenceValidationAuthController(
                evidenceValidationApi, reviews, identityApi);
        aiController = new AiProcessingController(
                aiProcessingApi, multiModelRoutingApi, executor, tenantAiPolicy, identityApi, null, null, null);
    }

    @AfterEach
    void tearDown() {
        TenantSecurityContext.clear();
    }

    @Test
    void extractionExecuteNextHandsOffMeetingNoteReadableViaAuthSurface() {
        ActorPrincipal admin = ActorPrincipal.operationsAdmin(userId);
        registry.registerModel(admin, registerModel("local-extract", 0.9));
        registry.configureCapability(admin, "local-extract", new ConfigureCapabilityCommand(
                ModelCapabilityType.TRANSCRIPT_EXTRACTION, 0.9, 0.8, 0, true));
        registry.registerDeployment(admin, deployment("local-extract", "extract-a"));
        registry.heartbeat(admin, "extract-a");

        UUID meetingOccurrenceId = UUID.randomUUID();
        UUID transcriptId = UUID.randomUUID();
        segmentSource.put(tenantId, transcriptId, List.of(
                new SegmentInput("seg-1", 0, "Alice", 0, 1000, "We decided to ship Friday.", true)
        ));
        provider.setResponse("""
                {
                  "topics": [{"text":"Delivery","evidenceSegmentIds":["seg-1"],"confidence":0.9}],
                  "decisions": [{"text":"Ship Friday","evidenceSegmentIds":["seg-1"],"confidence":0.9}],
                  "actionItems": [{"text":"Prepare release","owner":"Alice","dueDate":null,"evidenceSegmentIds":["seg-1"],"confidence":0.85}],
                  "risks": [],
                  "openQuestions": [],
                  "commitments": [],
                  "qualityFlags": [],
                  "evidenceSegmentIds": ["seg-1"],
                  "confidence": 0.9
                }
                """);

        bindPrincipal(Set.of(
                Permission.MEETING_WRITE.code(),
                Permission.MEETING_READ.code(),
                Permission.OPERATIONS_MANAGE.code()));

        var submitted = aiController.submit(new AiProcessingController.SubmitAiJobRequest(
                meetingOccurrenceId,
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

        var execution = aiController.executeNext().getBody();
        assertNotNull(execution);
        assertTrue(execution.succeeded());
        assertNotNull(execution.meetingNoteId());

        MeetingNoteDetailResponse note = miController.noteDetail(execution.meetingNoteId());
        assertEquals(meetingOccurrenceId, note.meetingOccurrenceId());
        assertFalse(note.decisions().isEmpty());
        assertEquals("Ship Friday", note.decisions().getFirst().text());
        assertFalse(note.actionItems().isEmpty());
        assertFalse(note.evidenceLinks().isEmpty());
        assertEquals("pv-meeting-chunk-extraction-v1", note.currentVersion().provenance().promptVersionId());
        assertFalse(audit.timelineFor(note.id()).isEmpty());
        assertEquals("MEETING_NOTE_MAPPED_FROM_AI", audit.timelineFor(note.id()).getFirst().action());
        assertEquals(
                QualityGateOutcome.PASSED,
                evidenceValidationApi.history(tenantId.value(), submitted.job().id()).getFirst().computedOutcome());
        assertTrue(validationController.manualReviewCases(
                com.nanobaseai.actenora.meetingintelligence.domain.validation.ManualReviewStatus.OPEN).isEmpty());
    }

    @Test
    void unknownOwnerBlocksHandoffAndOpensManualReview() {
        ActorPrincipal admin = ActorPrincipal.operationsAdmin(userId);
        registry.registerModel(admin, registerModel("local-extract", 0.9));
        registry.configureCapability(admin, "local-extract", new ConfigureCapabilityCommand(
                ModelCapabilityType.TRANSCRIPT_EXTRACTION, 0.9, 0.8, 0, true));
        registry.registerDeployment(admin, deployment("local-extract", "extract-a"));
        registry.heartbeat(admin, "extract-a");

        UUID transcriptId = UUID.randomUUID();
        // Owner "Ghost" is grounded in transcript text (AI pipeline OK) but is not a speaker/participant
        // (quality gate → MANUAL_REVIEW_REQUIRED). Draft note is still persisted for portal visibility.
        segmentSource.put(tenantId, transcriptId, List.of(
                new SegmentInput("seg-1", 0, "Alice", 0, 1000,
                        "Ghost will prepare the release. We decided to ship Friday.", true)
        ));
        provider.setResponse("""
                {
                  "topics": [],
                  "decisions": [{"text":"Ship Friday","evidenceSegmentIds":["seg-1"],"confidence":0.9}],
                  "actionItems": [{"text":"Prepare release","owner":"Ghost","dueDate":null,"evidenceSegmentIds":["seg-1"],"confidence":0.85}],
                  "risks": [],
                  "openQuestions": [],
                  "commitments": [],
                  "qualityFlags": [],
                  "evidenceSegmentIds": ["seg-1"],
                  "confidence": 0.9
                }
                """);

        bindPrincipal(Set.of(
                Permission.MEETING_WRITE.code(),
                Permission.MEETING_READ.code(),
                Permission.OPERATIONS_MANAGE.code()));
        var submitted = aiController.submit(new AiProcessingController.SubmitAiJobRequest(
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

        var execution = aiController.executeNext().getBody();
        assertNotNull(execution);
        assertTrue(execution.succeeded());
        assertNotNull(execution.meetingNoteId());
        assertEquals(
                QualityGateOutcome.MANUAL_REVIEW_REQUIRED,
                evidenceValidationApi.history(tenantId.value(), submitted.job().id()).getFirst().computedOutcome());
        assertEquals(1, validationController.manualReviewCases(
                com.nanobaseai.actenora.meetingintelligence.domain.validation.ManualReviewStatus.OPEN).size());
    }

    @Test
    void noteDetailDeniedWithoutPermission() {
        bindPrincipal(Set.of(Permission.MEETING_WRITE.code()));
        assertThrows(
                AuthorizationDeniedException.class,
                () -> miController.noteDetail(UUID.randomUUID()));
    }

    @Test
    void noteDetailDeniedForForeignTenant() {
        ActorPrincipal admin = ActorPrincipal.operationsAdmin(userId);
        registry.registerModel(admin, registerModel("local-extract", 0.9));
        registry.configureCapability(admin, "local-extract", new ConfigureCapabilityCommand(
                ModelCapabilityType.TRANSCRIPT_EXTRACTION, 0.9, 0.8, 0, true));
        registry.registerDeployment(admin, deployment("local-extract", "extract-a"));
        registry.heartbeat(admin, "extract-a");

        UUID transcriptId = UUID.randomUUID();
        segmentSource.put(tenantId, transcriptId, List.of(
                new SegmentInput("seg-1", 0, "Alice", 0, 1000, "Decision text.", true)
        ));
        provider.setResponse("""
                {
                  "topics": [],
                  "decisions": [{"text":"Go","evidenceSegmentIds":["seg-1"],"confidence":0.9}],
                  "actionItems": [],
                  "risks": [],
                  "openQuestions": [],
                  "commitments": [],
                  "qualityFlags": [],
                  "evidenceSegmentIds": ["seg-1"],
                  "confidence": 0.9
                }
                """);

        bindPrincipal(Set.of(
                Permission.MEETING_WRITE.code(),
                Permission.MEETING_READ.code(),
                Permission.OPERATIONS_MANAGE.code()));
        var submitted = aiController.submit(new AiProcessingController.SubmitAiJobRequest(
                UUID.randomUUID(),
                transcriptId,
                "CHUNK_EXTRACTION",
                JobPriority.NORMAL,
                AiCapability.TRANSCRIPT_EXTRACTION,
                "prompt-v1",
                "schema-v1",
                "tr",
                100,
                null,
                UUID.randomUUID()
        )).getBody();
        assertNotNull(submitted);
        UUID noteId = aiController.executeNext().getBody().meetingNoteId();
        assertNotNull(noteId);

        TenantSecurityContext.set(new AuthenticatedPrincipal(
                TenantId.random(),
                userId,
                "oid",
                "other@example.com",
                "Other",
                Set.of("OPERATIONS"),
                Set.of(Permission.MEETING_READ.code()),
                false
        ));
        assertThrows(
                com.nanobaseai.actenora.meetingintelligence.domain.exception.MeetingNoteNotFoundException.class,
                () -> miController.noteDetail(noteId));
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
