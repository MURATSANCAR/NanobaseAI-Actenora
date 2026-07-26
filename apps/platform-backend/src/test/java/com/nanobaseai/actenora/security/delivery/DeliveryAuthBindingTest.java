package com.nanobaseai.actenora.security.delivery;

import com.nanobaseai.actenora.approval.api.ApprovalApi;
import com.nanobaseai.actenora.approval.api.ApprovalDecisionType;
import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.approval.api.ApprovalRequestStatus;
import com.nanobaseai.actenora.approval.application.ApprovalWorkflowService;
import com.nanobaseai.actenora.approval.infrastructure.ApprovalApiAdapter;
import com.nanobaseai.actenora.approval.infrastructure.InMemoryApprovalRequestRepository;
import com.nanobaseai.actenora.approval.infrastructure.InMemoryParticipantDisputeRepository;
import com.nanobaseai.actenora.approval.infrastructure.RecordingApprovalAuditPort;
import com.nanobaseai.actenora.delivery.api.DeliveryApi;
import com.nanobaseai.actenora.delivery.application.DeliveryDispatcherService;
import com.nanobaseai.actenora.delivery.application.ExternalDeliveryService;
import com.nanobaseai.actenora.delivery.application.worker.DeliveryWorker;
import com.nanobaseai.actenora.delivery.domain.DeliveryDomainException;
import com.nanobaseai.actenora.delivery.domain.DeliveryOrderStatus;
import com.nanobaseai.actenora.delivery.domain.DeliveryStatus;
import com.nanobaseai.actenora.delivery.domain.exception.ExternalDeliveryBlockedException;
import com.nanobaseai.actenora.delivery.infrastructure.DeliveryApiAdapter;
import com.nanobaseai.actenora.delivery.infrastructure.approval.ApprovalApiNoteApprovalGate;
import com.nanobaseai.actenora.delivery.infrastructure.audit.RecordingDeliveryAuditPort;
import com.nanobaseai.actenora.delivery.infrastructure.mail.MailHogMailProvider;
import com.nanobaseai.actenora.delivery.infrastructure.pdf.InMemoryPdfAttachmentPort;
import com.nanobaseai.actenora.delivery.infrastructure.persistence.InMemoryDeliveryOrderRepository;
import com.nanobaseai.actenora.delivery.infrastructure.persistence.InMemoryDeliveryRequestRepository;
import com.nanobaseai.actenora.delivery.infrastructure.portal.HmacSignedPortalLinkService;
import com.nanobaseai.actenora.delivery.infrastructure.ratelimit.FixedWindowDeliveryRateLimiter;
import com.nanobaseai.actenora.identity.api.IdentityApi;
import com.nanobaseai.actenora.identity.domain.AuthorizationDeniedException;
import com.nanobaseai.actenora.identity.domain.Permission;
import com.nanobaseai.actenora.meetingintelligence.application.MeetingNoteApprovalService;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNote;
import com.nanobaseai.actenora.meetingintelligence.domain.model.ModelPromptSchemaProvenance;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.RecordingMeetingIntelligenceAuditPort;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryMeetingNoteRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryMeetingNoteVersionRepository;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;
import com.nanobaseai.actenora.sharedkernel.security.AuthenticatedPrincipal;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FAZ 19/20/23/30/31 — Delivery auth binding: READY → enqueue → drain → confirm / webhook.
 */
class DeliveryAuthBindingTest {

    private static final Instant NOW = Instant.parse("2026-07-25T22:30:00Z");

    private TenantId tenantId;
    private UUID userId;
    private MeetingNoteApprovalService noteApproval;
    private ApprovalApi approvalApi;
    private DeliveryAuthController controller;
    private DeliveryProviderWebhookController webhookController;
    private MailHogMailProvider mailProvider;
    private InMemoryDeliveryRequestRepository requests;

    @BeforeEach
    void setUp() {
        tenantId = TenantId.random();
        userId = UUID.randomUUID();
        Clock fixed = Clock.fixed(NOW, ZoneOffset.UTC);
        InstantClock clock = new InstantClock(fixed);

        approvalApi = new ApprovalApiAdapter(new ApprovalWorkflowService(
                new InMemoryApprovalRequestRepository(),
                new InMemoryParticipantDisputeRepository(),
                new RecordingApprovalAuditPort(),
                fixed
        ));
        noteApproval = new MeetingNoteApprovalService(
                new InMemoryMeetingNoteRepository(),
                new InMemoryMeetingNoteVersionRepository(),
                approvalApi,
                new RecordingMeetingIntelligenceAuditPort(),
                fixed
        );

        requests = new InMemoryDeliveryRequestRepository();
        mailProvider = MailHogMailProvider.localDefaults(clock::now);
        RecordingDeliveryAuditPort audit = new RecordingDeliveryAuditPort();
        DeliveryDispatcherService dispatcher = new DeliveryDispatcherService(
                requests,
                new ApprovalApiNoteApprovalGate(approvalApi),
                mailProvider,
                new FixedWindowDeliveryRateLimiter(60, clock),
                new InMemoryPdfAttachmentPort(),
                new HmacSignedPortalLinkService("test-portal-secret-key", "https://portal.test"),
                audit,
                clock
        );
        ExternalDeliveryService external = new ExternalDeliveryService(
                approvalApi,
                new InMemoryDeliveryOrderRepository(),
                audit,
                fixed
        );
        DeliveryApi deliveryApi = new DeliveryApiAdapter(external, dispatcher, requests);
        DeliveryWorker worker = new DeliveryWorker(requests, dispatcher, clock, 32);
        controller = new DeliveryAuthController(deliveryApi, approvalApi, worker, stubIdentityApi());
        webhookController = new DeliveryProviderWebhookController(deliveryApi, "test-webhook-secret");
    }

    @AfterEach
    void tearDown() {
        TenantSecurityContext.clear();
    }

    @Test
    void grantedApprovalCreatesIdempotentReadyOrder() {
        MeetingNote note = draft();
        ApprovalId approvalId = approve(note);
        bind(Set.of(Permission.DELIVERY_MANAGE.code()));

        var first = controller.requestOrder(new DeliveryAuthController.RequestDeliveryOrderBody(
                approvalId.value(), note.currentVersionId(), "email"));
        assertEquals(DeliveryOrderStatus.READY, first.status());

        var second = controller.requestOrder(new DeliveryAuthController.RequestDeliveryOrderBody(
                approvalId.value(), null, "email"));
        assertEquals(first.id(), second.id());
        assertEquals(ApprovalRequestStatus.GRANTED,
                approvalApi.status(tenantId.value(), approvalId).orElseThrow());
    }

    @Test
    void enqueueFromReadyOrderThenDrainCapturesMail() {
        MeetingNote note = draft();
        ApprovalId approvalId = approve(note);
        bind(Set.of(Permission.DELIVERY_MANAGE.code()));

        var order = controller.requestOrder(new DeliveryAuthController.RequestDeliveryOrderBody(
                approvalId.value(), note.currentVersionId(), "email"));

        var enqueued = controller.enqueue(order.id(), new DeliveryAuthController.EnqueueDeliveryBody(
                List.of(new DeliveryAuthController.RecipientBody("alice@ex.com", "EXTERNAL", "Alice")),
                "Ship notes",
                "Please review."
        ));
        assertEquals(1, enqueued.createdIds().size());
        assertEquals(0, enqueued.duplicateIds().size());

        var duplicate = controller.enqueue(order.id(), new DeliveryAuthController.EnqueueDeliveryBody(
                List.of(new DeliveryAuthController.RecipientBody("alice@ex.com", "EXTERNAL", "Alice")),
                "Ship notes",
                "Please review."
        ));
        assertEquals(0, duplicate.createdIds().size());
        assertEquals(1, duplicate.duplicateIds().size());
        assertEquals(enqueued.createdIds().getFirst(), duplicate.duplicateIds().getFirst());

        assertEquals(DeliveryStatus.QUEUED,
                controller.requestStatus(enqueued.createdIds().getFirst()).status());

        var drain = controller.drain();
        assertEquals(1, drain.processed());
        assertEquals(DeliveryStatus.PROVIDER_ACCEPTED, drain.outcomes().getFirst());
        assertEquals(1, mailProvider.sentMails().size());
        assertEquals("alice@ex.com", mailProvider.sentMails().getFirst().to());
        assertTrue(mailProvider.sentMails().getFirst().subject().contains("Ship"));

        UUID requestId = enqueued.createdIds().getFirst();
        assertEquals(DeliveryStatus.PROVIDER_ACCEPTED, controller.requestStatus(requestId).status());
        assertEquals(DeliveryStatus.DELIVERED, controller.confirmDelivered(requestId).status());
        assertEquals(DeliveryStatus.DELIVERED, controller.requestStatus(requestId).status());
    }

    @Test
    void providerWebhookConfirmsDeliveredWithSharedSecret() {
        MeetingNote note = draft();
        ApprovalId approvalId = approve(note);
        bind(Set.of(Permission.DELIVERY_MANAGE.code()));
        var order = controller.requestOrder(new DeliveryAuthController.RequestDeliveryOrderBody(
                approvalId.value(), note.currentVersionId(), "email"));
        var enqueued = controller.enqueue(order.id(), new DeliveryAuthController.EnqueueDeliveryBody(
                List.of(new DeliveryAuthController.RecipientBody("carol@ex.com", "EXTERNAL", "Carol")),
                "Webhook ship",
                "Body"
        ));
        UUID requestId = enqueued.createdIds().getFirst();
        assertEquals(1, controller.drain().processed());
        assertEquals(DeliveryStatus.PROVIDER_ACCEPTED, controller.requestStatus(requestId).status());

        TenantSecurityContext.clear();
        var confirmed = webhookController.providerDelivered(
                "test-webhook-secret",
                new DeliveryProviderWebhookController.ProviderDeliveredBody(
                        tenantId.value(), requestId, "mailhog-1", "delivered"
                )
        );
        assertEquals(DeliveryStatus.DELIVERED, confirmed.status());

        var duplicate = webhookController.providerDelivered(
                "test-webhook-secret",
                new DeliveryProviderWebhookController.ProviderDeliveredBody(
                        tenantId.value(), requestId, "mailhog-1", "delivered"
                )
        );
        assertEquals(DeliveryStatus.DELIVERED, duplicate.status());
    }

    @Test
    void providerWebhookConfirmsUsingProviderMessageIdAlone() {
        MeetingNote note = draft();
        ApprovalId approvalId = approve(note);
        bind(Set.of(Permission.DELIVERY_MANAGE.code()));
        var order = controller.requestOrder(new DeliveryAuthController.RequestDeliveryOrderBody(
                approvalId.value(), note.currentVersionId(), "email"));
        var enqueued = controller.enqueue(order.id(), new DeliveryAuthController.EnqueueDeliveryBody(
                List.of(new DeliveryAuthController.RecipientBody("dave@ex.com", "EXTERNAL", "Dave")),
                "Provider id ship",
                "Body"
        ));
        UUID requestId = enqueued.createdIds().getFirst();
        assertEquals(1, controller.drain().processed());

        String providerMessageId = requests.findById(tenantId, requestId)
                .flatMap(r -> r.latestAttempt().flatMap(a -> a.providerMessage()))
                .map(m -> m.providerMessageId())
                .orElseThrow();

        TenantSecurityContext.clear();
        var confirmed = webhookController.providerDelivered(
                "test-webhook-secret",
                new DeliveryProviderWebhookController.ProviderDeliveredBody(
                        tenantId.value(), null, providerMessageId, "delivered"
                )
        );
        assertEquals(requestId, confirmed.deliveryRequestId());
        assertEquals(DeliveryStatus.DELIVERED, confirmed.status());
        assertEquals(providerMessageId, confirmed.providerMessageId());
    }

    @Test
    void providerWebhookRejectsBadSecret() {
        assertThrows(ActenoraException.class, () ->
                webhookController.providerDelivered(
                        "wrong-secret",
                        new DeliveryProviderWebhookController.ProviderDeliveredBody(
                                tenantId.value(), UUID.randomUUID(), null, "delivered"
                        )
                ));
    }

    @Test
    void confirmDeliveredRejectedWhenStillQueued() {
        MeetingNote note = draft();
        ApprovalId approvalId = approve(note);
        bind(Set.of(Permission.DELIVERY_MANAGE.code()));
        var order = controller.requestOrder(new DeliveryAuthController.RequestDeliveryOrderBody(
                approvalId.value(), note.currentVersionId(), "email"));
        var enqueued = controller.enqueue(order.id(), new DeliveryAuthController.EnqueueDeliveryBody(
                List.of(new DeliveryAuthController.RecipientBody("bob@ex.com", "EXTERNAL", "Bob")),
                "Subject",
                "Body"
        ));
        assertThrows(DeliveryDomainException.class, () ->
                controller.confirmDelivered(enqueued.createdIds().getFirst()));
    }

    @Test
    void pendingApprovalBlocksDelivery() {
        MeetingNote note = draft();
        ApprovalId approvalId = noteApproval.submitForApproval(
                tenantId.value(), note.id(), userId.toString(), null, 0L);
        bind(Set.of(Permission.DELIVERY_MANAGE.code()));

        assertThrows(ExternalDeliveryBlockedException.class, () ->
                controller.requestOrder(new DeliveryAuthController.RequestDeliveryOrderBody(
                        approvalId.value(), note.currentVersionId(), "email")));
    }

    @Test
    void deniedWithoutPermission() {
        bind(Set.of(Permission.MEETING_READ.code()));
        assertThrows(AuthorizationDeniedException.class, () ->
                controller.requestOrder(new DeliveryAuthController.RequestDeliveryOrderBody(
                        UUID.randomUUID(), UUID.randomUUID(), "email")));
    }

    @Test
    void foreignTenantCannotReadOrder() {
        MeetingNote note = draft();
        ApprovalId approvalId = approve(note);
        bind(Set.of(Permission.DELIVERY_MANAGE.code()));
        var order = controller.requestOrder(new DeliveryAuthController.RequestDeliveryOrderBody(
                approvalId.value(), note.currentVersionId(), "email"));

        TenantSecurityContext.set(new AuthenticatedPrincipal(
                TenantId.random(),
                userId,
                "oid",
                "other@example.com",
                "Other",
                Set.of("OPERATIONS"),
                Set.of(Permission.DELIVERY_MANAGE.code()),
                false
        ));
        assertThrows(ActenoraException.class, () -> controller.getOrder(order.id()));
    }

    private ApprovalId approve(MeetingNote note) {
        ApprovalId approvalId = noteApproval.submitForApproval(
                tenantId.value(), note.id(), userId.toString(), null, 0L);
        noteApproval.decideApproval(
                tenantId.value(), note.id(), approvalId, userId.toString(),
                ApprovalDecisionType.APPROVE, "ok", 1L, 0L
        );
        return approvalId;
    }

    private MeetingNote draft() {
        return noteApproval.createDraft(
                tenantId.value(),
                UUID.randomUUID(),
                "Ship Friday",
                ModelPromptSchemaProvenance.of("m", "p", "s", 0.9)
        );
    }

    private void bind(Set<String> permissions) {
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
                return List.of();
            }

            @Override
            public com.nanobaseai.actenora.identity.api.UserView grantRole(
                    TenantId tenantId, UUID userId,
                    com.nanobaseai.actenora.identity.domain.SystemRole role,
                    long expectedVersion, UUID actorUserId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public com.nanobaseai.actenora.identity.api.UserView revokeRole(
                    TenantId tenantId, UUID userId,
                    com.nanobaseai.actenora.identity.domain.SystemRole role,
                    long expectedVersion, UUID actorUserId) {
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
