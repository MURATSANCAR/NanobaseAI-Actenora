package com.nanobaseai.actenora.security.approval;

import com.nanobaseai.actenora.approval.api.ApprovalApi;
import com.nanobaseai.actenora.approval.api.ApprovalRequestStatus;
import com.nanobaseai.actenora.approval.application.ApprovalWorkflowService;
import com.nanobaseai.actenora.approval.domain.exception.UnauthorizedApprovalException;
import com.nanobaseai.actenora.approval.infrastructure.ApprovalApiAdapter;
import com.nanobaseai.actenora.approval.infrastructure.InMemoryApprovalRequestRepository;
import com.nanobaseai.actenora.approval.infrastructure.InMemoryParticipantDisputeRepository;
import com.nanobaseai.actenora.approval.infrastructure.RecordingApprovalAuditPort;
import com.nanobaseai.actenora.identity.api.IdentityApi;
import com.nanobaseai.actenora.identity.domain.AuthorizationDeniedException;
import com.nanobaseai.actenora.identity.api.Permission;
import com.nanobaseai.actenora.meetingintelligence.api.MeetingIntelligenceApi;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteDetailResponse;
import com.nanobaseai.actenora.meetingintelligence.api.ledger.ContinuityLedgerApi;
import com.nanobaseai.actenora.meetingintelligence.application.MeetingNoteApprovalService;
import com.nanobaseai.actenora.meetingintelligence.application.ledger.ContinuityLedgerService;
import com.nanobaseai.actenora.meetingintelligence.application.port.CommitmentRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.DecisionRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.event.LedgerEventType;
import com.nanobaseai.actenora.meetingintelligence.domain.model.Commitment;
import com.nanobaseai.actenora.meetingintelligence.domain.model.Decision;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNote;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNoteStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.model.ModelPromptSchemaProvenance;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.RecordingMeetingIntelligenceAuditPort;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.ledger.InMemoryLedgerEventStore;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.ledger.InMemoryLedgerProjectionRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryMeetingNoteRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryMeetingNoteVersionRepository;
import com.nanobaseai.actenora.security.meetingintelligence.ApprovedNoteLedgerAdapter;
import com.nanobaseai.actenora.security.meetingintelligence.MeetingIntelligenceAuthController;
import com.nanobaseai.actenora.security.meetingintelligence.MeetingIntelligencePlatformConfiguration;
import com.nanobaseai.actenora.security.meetingintelligence.NoteApprovedForLedgerHandler;
import com.nanobaseai.actenora.security.meetingintelligence.OutboxApprovedNoteLedgerAdapter;
import com.nanobaseai.actenora.meetingintelligence.api.event.MeetingIntelligenceIntegrationEvents;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.EventBackbone;
import com.nanobaseai.actenora.sharedkernel.messaging.EventMessagingConfig;
import com.nanobaseai.actenora.sharedkernel.messaging.OutboxStatus;
import com.nanobaseai.actenora.sharedkernel.messaging.inbox.IdempotentEventConsumer;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.FanOutEventTransport;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryDeadLetterStore;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryInboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryOutboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.support.TenantFairnessTracker;
import com.nanobaseai.actenora.sharedkernel.security.AuthenticatedPrincipal;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FAZ 18 — Approval auth binding + note status synchronization.
 * FAZ 29 — Approved-note ledger handoff via transactional outbox relay.
 */
class ApprovalAuthBindingTest {

    private static final Instant NOW = Instant.parse("2026-07-25T22:00:00Z");

    private TenantId tenantId;
    private UUID approverUserId;
    private UUID otherUserId;
    private MeetingNoteApprovalService noteApprovalService;
    private MeetingIntelligenceApi meetingIntelligenceApi;
    private ApprovalApi approvalApi;
    private ApprovalAuthController approvalController;
    private MeetingIntelligenceAuthController miController;
    private InMemoryMeetingNoteVersionRepository versions;
    private RecordingMeetingIntelligenceAuditPort audit;
    private DecisionRepository decisions;
    private CommitmentRepository commitments;
    private ContinuityLedgerApi ledgerApi;
    private ApprovedNoteLedgerAdapter approvedNoteLedgerWriter;
    private EventBackbone eventBackbone;
    private IdempotentEventConsumer ledgerConsumer;
    private AtomicInteger ledgerHandlerCalls;

    @BeforeEach
    void setUp() {
        tenantId = TenantId.random();
        approverUserId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        versions = new InMemoryMeetingNoteVersionRepository();
        InMemoryMeetingNoteRepository notes = new InMemoryMeetingNoteRepository();
        audit = new RecordingMeetingIntelligenceAuditPort();

        ApprovalWorkflowService workflow = new ApprovalWorkflowService(
                new InMemoryApprovalRequestRepository(),
                new InMemoryParticipantDisputeRepository(),
                new RecordingApprovalAuditPort(),
                clock
        );
        approvalApi = new ApprovalApiAdapter(workflow);

        MeetingIntelligencePlatformConfiguration miConfig = new MeetingIntelligencePlatformConfiguration();
        decisions = miConfig.inMemoryDecisionRepository();
        var actionItems = miConfig.inMemoryActionItemRepository();
        var risks = miConfig.inMemoryRiskRepository();
        commitments = miConfig.inMemoryCommitmentRepository();
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
        ledgerApi = new ContinuityLedgerApi(new ContinuityLedgerService(
                new InMemoryLedgerEventStore(),
                new InMemoryLedgerProjectionRepository(),
                clock
        ));
        approvedNoteLedgerWriter = new ApprovedNoteLedgerAdapter(
                decisions, actionItems, commitments, risks, openQuestions, ledgerApi);

        TenantFairnessTracker fairness = new TenantFairnessTracker();
        InMemoryOutboxStore outbox = new InMemoryOutboxStore(fairness);
        FanOutEventTransport transport = new FanOutEventTransport();
        eventBackbone = EventBackbone.of(
                EventMessagingConfig.defaults("platform"),
                outbox,
                new InMemoryInboxStore(),
                new InMemoryDeadLetterStore(),
                transport,
                fairness
        );
        ledgerConsumer = eventBackbone.consumer("meeting-intelligence");
        ledgerHandlerCalls = new AtomicInteger();
        NoteApprovedForLedgerHandler handler = new NoteApprovedForLedgerHandler(approvedNoteLedgerWriter);
        transport.subscribe(envelope -> {
            if (MeetingIntelligenceIntegrationEvents.NOTE_APPROVED_FOR_LEDGER.equals(envelope.eventType())) {
                ledgerHandlerCalls.incrementAndGet();
                ledgerConsumer.consume(envelope, handler::handle);
            }
        });

        noteApprovalService = new MeetingNoteApprovalService(
                notes,
                versions,
                approvalApi,
                audit,
                new OutboxApprovedNoteLedgerAdapter(eventBackbone.outboxPublisher(), "meeting-intelligence"),
                clock
        );

        IdentityApi identityApi = stubIdentityApi();
        approvalController = new ApprovalAuthController(
                noteApprovalService, approvalApi, meetingIntelligenceApi, identityApi);
        miController = new MeetingIntelligenceAuthController(meetingIntelligenceApi, identityApi);
    }

    @AfterEach
    void tearDown() {
        TenantSecurityContext.clear();
        if (eventBackbone != null) {
            eventBackbone.close();
        }
    }

    @Test
    void submitAndApproveSynchronizesNoteVersionStatus() {
        MeetingNote draft = noteApprovalService.createDraft(
                tenantId.value(),
                UUID.randomUUID(),
                "Ship Friday",
                ModelPromptSchemaProvenance.of("m", "p", "s", 0.9)
        );
        bindPrincipal(approverUserId, Set.of(
                Permission.MEETING_WRITE.code(),
                Permission.MEETING_READ.code(),
                Permission.APPROVAL_DECIDE.code()));

        var submitted = approvalController.submitForApproval(
                draft.id(),
                new ApprovalAuthController.SubmitApprovalRequest(approverUserId.toString(), null, 0L)
        );
        assertEquals(ApprovalRequestStatus.PENDING, submitted.status());
        assertEquals(MeetingNoteStatus.PENDING_APPROVAL,
                versions.findByIdAndTenantId(draft.currentVersionId(), tenantId).orElseThrow().approvalStatus());

        var decided = approvalController.decide(
                submitted.approvalId(),
                new ApprovalAuthController.DecideApprovalRequest("APPROVE", "looks good", 1L, 0L)
        );
        assertEquals(ApprovalRequestStatus.GRANTED, decided.status());
        assertEquals(draft.id(), decided.noteId());
        assertEquals(MeetingNoteStatus.APPROVED,
                versions.findByIdAndTenantId(draft.currentVersionId(), tenantId).orElseThrow().approvalStatus());
        assertTrue(approvalApi.isGrantedForSubject(
                tenantId.value(),
                com.nanobaseai.actenora.approval.api.ApprovalId.of(submitted.approvalId()),
                draft.currentVersionId()));
        assertTrue(audit.timelineFor(draft.id()).stream()
                .anyMatch(e -> e.action().equals("NOTE_APPROVAL_GRANTED")));

        MeetingNoteDetailResponse note = miController.noteDetail(draft.id());
        assertEquals(MeetingNoteStatus.APPROVED, note.currentVersion().approvalStatus());
    }

    @Test
    void approvalAppendsCurrentVersionArtifactsToContinuityLedger() {
        UUID occurrenceId = UUID.randomUUID();
        MeetingNote draft = noteApprovalService.createDraft(
                tenantId.value(),
                occurrenceId,
                "Ship Friday",
                ModelPromptSchemaProvenance.of("m", "p", "s", 0.9)
        );
        Decision decision = decisions.save(Decision.createFromMapping(
                tenantId,
                draft.id(),
                draft.currentVersionId(),
                "Adopt local inference",
                false,
                0.94,
                NOW
        ));
        Commitment commitment = commitments.save(Commitment.createFromMapping(
                tenantId,
                draft.id(),
                draft.currentVersionId(),
                "Publish migration plan",
                "alice@example.com",
                false,
                0.91,
                NOW
        ));
        bindPrincipal(approverUserId, Set.of(
                Permission.MEETING_WRITE.code(),
                Permission.MEETING_READ.code(),
                Permission.APPROVAL_DECIDE.code()));

        var submitted = approvalController.submitForApproval(
                draft.id(),
                new ApprovalAuthController.SubmitApprovalRequest(approverUserId.toString(), null, 0L)
        );
        approvalController.decide(
                submitted.approvalId(),
                new ApprovalAuthController.DecideApprovalRequest("APPROVE", "publish", 1L, 0L)
        );

        assertEquals(0, ledgerApi.listEvents(tenantId).size());
        assertEquals(1, eventBackbone.outboxStore().countByStatus(OutboxStatus.PENDING));

        assertEquals(1, eventBackbone.relay().publishDueBatch());
        assertEquals(1, ledgerHandlerCalls.get());

        var events = ledgerApi.listEvents(tenantId);
        assertEquals(2, events.size());
        assertEquals(LedgerEventType.DECISION_RECORDED, events.get(0).type());
        assertEquals(decision.id(), events.get(0).aggregateId());
        assertEquals(LedgerEventType.COMMITMENT_RECORDED, events.get(1).type());
        assertEquals(commitment.id(), events.get(1).aggregateId());
        assertTrue(events.stream().allMatch(event -> event.meetingOccurrenceId().equals(occurrenceId)));

        var published = ((FanOutEventTransport) eventBackbone.transport()).published().stream()
                .filter(e -> MeetingIntelligenceIntegrationEvents.NOTE_APPROVED_FOR_LEDGER.equals(e.eventType()))
                .findFirst()
                .orElseThrow();
        assertEquals(
                IdempotentEventConsumer.Outcome.DUPLICATE,
                ledgerConsumer.consume(published, env -> {
                }).outcome()
        );
        assertEquals(2, ledgerApi.listEvents(tenantId).size());
    }

    @Test
    void decideDeniedWithoutPermission() {
        MeetingNote draft = noteApprovalService.createDraft(
                tenantId.value(), UUID.randomUUID(), "x",
                ModelPromptSchemaProvenance.of("m", "p", "s", 0.9));
        bindPrincipal(approverUserId, Set.of(Permission.MEETING_WRITE.code(), Permission.MEETING_READ.code()));
        var submitted = approvalController.submitForApproval(
                draft.id(),
                new ApprovalAuthController.SubmitApprovalRequest(approverUserId.toString(), null, 0L)
        );
        assertThrows(AuthorizationDeniedException.class, () ->
                approvalController.decide(
                        submitted.approvalId(),
                        new ApprovalAuthController.DecideApprovalRequest("APPROVE", "nope", 1L, 0L)
                ));
    }

    @Test
    void wrongApproverForbidden() {
        MeetingNote draft = noteApprovalService.createDraft(
                tenantId.value(), UUID.randomUUID(), "x",
                ModelPromptSchemaProvenance.of("m", "p", "s", 0.9));
        bindPrincipal(approverUserId, Set.of(
                Permission.MEETING_WRITE.code(),
                Permission.MEETING_READ.code(),
                Permission.APPROVAL_DECIDE.code()));
        var submitted = approvalController.submitForApproval(
                draft.id(),
                new ApprovalAuthController.SubmitApprovalRequest(approverUserId.toString(), null, 0L)
        );

        bindPrincipal(otherUserId, Set.of(Permission.APPROVAL_DECIDE.code(), Permission.MEETING_READ.code()));
        assertThrows(UnauthorizedApprovalException.class, () ->
                approvalController.decide(
                        submitted.approvalId(),
                        new ApprovalAuthController.DecideApprovalRequest("APPROVE", "hijack", 1L, 0L)
                ));
    }

    @Test
    void getApprovalDeniedForForeignTenant() {
        MeetingNote draft = noteApprovalService.createDraft(
                tenantId.value(), UUID.randomUUID(), "x",
                ModelPromptSchemaProvenance.of("m", "p", "s", 0.9));
        bindPrincipal(approverUserId, Set.of(
                Permission.MEETING_WRITE.code(),
                Permission.MEETING_READ.code()));
        var submitted = approvalController.submitForApproval(
                draft.id(),
                new ApprovalAuthController.SubmitApprovalRequest(approverUserId.toString(), null, 0L)
        );
        assertNotNull(approvalController.getApproval(submitted.approvalId()));

        TenantSecurityContext.set(new AuthenticatedPrincipal(
                TenantId.random(),
                approverUserId,
                "oid",
                "other@example.com",
                "Other",
                Set.of("OPERATIONS"),
                Set.of(Permission.MEETING_READ.code()),
                false
        ));
        assertThrows(com.nanobaseai.actenora.sharedkernel.error.ActenoraException.class, () ->
                approvalController.getApproval(submitted.approvalId()));
    }

    private void bindPrincipal(UUID userId, Set<String> permissions) {
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
                return java.util.List.of();
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
