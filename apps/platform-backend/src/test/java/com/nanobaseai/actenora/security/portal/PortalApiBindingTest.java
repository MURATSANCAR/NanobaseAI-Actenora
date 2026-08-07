package com.nanobaseai.actenora.security.portal;

import com.nanobaseai.actenora.approval.api.ApprovalApi;
import com.nanobaseai.actenora.approval.api.ApprovalSubjectType;
import com.nanobaseai.actenora.approval.application.ApprovalWorkflowService;
import com.nanobaseai.actenora.approval.infrastructure.ApprovalApiAdapter;
import com.nanobaseai.actenora.approval.infrastructure.InMemoryApprovalRequestRepository;
import com.nanobaseai.actenora.approval.infrastructure.InMemoryParticipantDisputeRepository;
import com.nanobaseai.actenora.approval.infrastructure.RecordingApprovalAuditPort;
import com.nanobaseai.actenora.audit.api.AuditApi;
import com.nanobaseai.actenora.audit.application.AuditAppendService;
import com.nanobaseai.actenora.audit.infrastructure.AuditApiAdapter;
import com.nanobaseai.actenora.audit.infrastructure.InMemoryAuditEntryStore;
import com.nanobaseai.actenora.identity.api.IdentityApi;
import com.nanobaseai.actenora.identity.api.UserView;
import com.nanobaseai.actenora.identity.api.Permission;
import com.nanobaseai.actenora.identity.domain.SystemRole;
import com.nanobaseai.actenora.identity.domain.UserStatus;
import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meeting.api.dto.CreateBusinessContextRequest;
import com.nanobaseai.actenora.meeting.api.dto.CreateMeetingRequest;
import com.nanobaseai.actenora.meeting.api.dto.MeetingResponse;
import com.nanobaseai.actenora.meeting.application.BusinessContextApplicationService;
import com.nanobaseai.actenora.meeting.application.MeetingApiFacade;
import com.nanobaseai.actenora.meeting.application.MeetingApplicationService;
import com.nanobaseai.actenora.meeting.domain.model.MeetingType;
import com.nanobaseai.actenora.meeting.domain.model.ProcessingPriority;
import com.nanobaseai.actenora.meeting.infrastructure.audit.InMemoryMeetingAuditPort;
import com.nanobaseai.actenora.meeting.infrastructure.messaging.InMemoryMeetingEventPublisher;
import com.nanobaseai.actenora.meeting.infrastructure.persistence.InMemoryBusinessContextRepository;
import com.nanobaseai.actenora.meeting.infrastructure.persistence.InMemoryMeetingOccurrenceRepository;
import com.nanobaseai.actenora.meeting.infrastructure.persistence.InMemoryMeetingParticipantRepository;
import com.nanobaseai.actenora.meeting.infrastructure.persistence.InMemoryMeetingSeriesRepository;
import com.nanobaseai.actenora.meeting.infrastructure.quota.NoOpMeetingQuotaPort;
import com.nanobaseai.actenora.meeting.infrastructure.tenancy.FixedTenantContext;
import com.nanobaseai.actenora.meeting.infrastructure.time.SystemClockPort;
import com.nanobaseai.actenora.meetingintelligence.api.MeetingIntelligenceApi;
import com.nanobaseai.actenora.meetingintelligence.api.ledger.ContinuityLedgerApi;
import com.nanobaseai.actenora.meetingintelligence.application.MeetingNoteApprovalService;
import com.nanobaseai.actenora.operations.api.OperationsApi;
import com.nanobaseai.actenora.transcript.api.TranscriptApi;
import com.nanobaseai.actenora.meetingintelligence.application.ledger.ContinuityLedgerService;
import com.nanobaseai.actenora.meetingintelligence.application.port.ApprovedNoteLedgerPort;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.RecordingMeetingIntelligenceAuditPort;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.ledger.InMemoryLedgerEventStore;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.ledger.InMemoryLedgerProjectionRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryMeetingNoteRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryMeetingNoteVersionRepository;
import com.nanobaseai.actenora.notification.api.NotificationApi;
import com.nanobaseai.actenora.notification.application.UserNotificationService;
import com.nanobaseai.actenora.notification.domain.UserNotificationType;
import com.nanobaseai.actenora.notification.infrastructure.persistence.InMemoryUserNotificationRepository;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.security.AuthenticatedPrincipal;
import com.nanobaseai.actenora.sharedkernel.security.IdentityClaims;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import com.nanobaseai.actenora.template.application.DocumentRenderService;
import com.nanobaseai.actenora.template.application.TemplateStudioService;
import com.nanobaseai.actenora.template.domain.SchemaJsonParser;
import com.nanobaseai.actenora.template.domain.TemplateDomainException;
import com.nanobaseai.actenora.template.infrastructure.TemplateApiFacade;
import com.nanobaseai.actenora.template.infrastructure.persistence.InMemoryMeetingTemplateRepository;
import com.nanobaseai.actenora.template.infrastructure.persistence.InMemoryNoteTemplateLockRepository;
import com.nanobaseai.actenora.template.infrastructure.persistence.InMemoryRenderJobRepository;
import com.nanobaseai.actenora.template.infrastructure.persistence.InMemoryRenderedDocumentRepository;
import com.nanobaseai.actenora.template.api.TemplateApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FAZ 33 — portal BFF returns portal-shaped DTOs for me / dashboard / meetings.
 */
class PortalApiBindingTest {

    private TenantId tenantId;
    private UUID userId;
    private PortalApiController controller;
    private MeetingApi meetingApi;
    private ContinuityLedgerService ledgerService;
    private ApprovalApi approvalApi;
    private AuditApi auditApi;
    private NotificationApi notificationApi;

    @BeforeEach
    void setUp() {
        tenantId = TenantId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        FixedTenantContext tenantContext = new FixedTenantContext(tenantId, userId);
        InMemoryBusinessContextRepository contexts = new InMemoryBusinessContextRepository();
        InMemoryMeetingOccurrenceRepository occurrences = new InMemoryMeetingOccurrenceRepository();
        InMemoryMeetingParticipantRepository participants = new InMemoryMeetingParticipantRepository();
        MeetingApplicationService meetingService = new MeetingApplicationService(
                tenantContext,
                contexts,
                new InMemoryMeetingSeriesRepository(),
                occurrences,
                participants,
                new InMemoryMeetingEventPublisher(),
                new InMemoryMeetingAuditPort(),
                new NoOpMeetingQuotaPort(),
                new SystemClockPort()
        );
        BusinessContextApplicationService businessContexts = new BusinessContextApplicationService(
                tenantContext, contexts, new InMemoryMeetingAuditPort(), new SystemClockPort()
        );
        meetingApi = new MeetingApiFacade(meetingService, businessContexts);

        ledgerService = new ContinuityLedgerService(
                new InMemoryLedgerEventStore(),
                new InMemoryLedgerProjectionRepository(),
                Clock.systemUTC()
        );
        ContinuityLedgerApi ledgerApi = new ContinuityLedgerApi(ledgerService);

        approvalApi = new ApprovalApiAdapter(
                new ApprovalWorkflowService(
                        new InMemoryApprovalRequestRepository(),
                        new InMemoryParticipantDisputeRepository(),
                        new RecordingApprovalAuditPort(),
                        Clock.systemUTC()
                )
        );

        MeetingNoteApprovalService noteApproval = new MeetingNoteApprovalService(
                new InMemoryMeetingNoteRepository(),
                new InMemoryMeetingNoteVersionRepository(),
                approvalApi,
                new RecordingMeetingIntelligenceAuditPort(),
                (ApprovedNoteLedgerPort) (t, meetingOccurrenceId, noteId, noteVersionId) -> { },
                Clock.systemUTC()
        );

        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        ObjectProvider<MeetingIntelligenceApi> intelligenceProvider =
                beanFactory.getBeanProvider(MeetingIntelligenceApi.class);
        ObjectProvider<OperationsApi> operationsProvider = beanFactory.getBeanProvider(OperationsApi.class);
        ObjectProvider<TranscriptApi> transcriptProvider = beanFactory.getBeanProvider(TranscriptApi.class);

        InMemoryMeetingTemplateRepository templateRepo = new InMemoryMeetingTemplateRepository();
        InMemoryNoteTemplateLockRepository templateLocks = new InMemoryNoteTemplateLockRepository();
        InMemoryRenderJobRepository renderJobs = new InMemoryRenderJobRepository();
        InMemoryRenderedDocumentRepository renderedDocuments = new InMemoryRenderedDocumentRepository();
        InstantClock templateClock = new InstantClock(Clock.systemUTC());
        TemplateStudioService templateStudio = new TemplateStudioService(
                templateRepo,
                templateLocks,
                new SchemaJsonParser(new ObjectMapper()),
                templateClock
        );
        DocumentRenderService renderService = new DocumentRenderService(
                templateRepo,
                templateLocks,
                renderJobs,
                renderedDocuments,
                templateClock
        );
        TemplateApi templateApi = new TemplateApiFacade(
                templateStudio,
                renderService,
                renderedDocuments,
                renderJobs
        );

        StaticListableBeanFactory optionalBeans = new StaticListableBeanFactory();
        optionalBeans.addBean("templateApi", templateApi);
        auditApi = new AuditApiAdapter(new AuditAppendService(new InMemoryAuditEntryStore()));
        optionalBeans.addBean("auditApi", auditApi);

        InMemoryUserNotificationRepository notificationRepository = new InMemoryUserNotificationRepository();
        NotificationApi notificationApi = new UserNotificationService(
                notificationRepository,
                new InstantClock(Clock.systemUTC())
        );
        optionalBeans.addBean("notificationApi", notificationApi);
        this.notificationApi = notificationApi;

        controller = new PortalApiController(
                stubIdentityApi(),
                meetingApi,
                ledgerApi,
                approvalApi,
                noteApproval,
                intelligenceProvider,
                operationsProvider,
                transcriptProvider,
                optionalBeans.getBeanProvider(TemplateApi.class),
                optionalBeans.getBeanProvider(com.nanobaseai.actenora.microsoftconnection.api.MicrosoftConnectionApi.class),
                optionalBeans.getBeanProvider(com.nanobaseai.actenora.modelmanagement.api.ModelManagementApi.class),
                optionalBeans.getBeanProvider(com.nanobaseai.actenora.security.aiprocessing.NanobaseAiConnectionService.class),
                optionalBeans.getBeanProvider(com.nanobaseai.actenora.security.microsoftconnection.GraphConnectionService.class),
                optionalBeans.getBeanProvider(com.nanobaseai.actenora.aiprocessing.api.AiProcessingApi.class),
                optionalBeans.getBeanProvider(AuditApi.class),
                optionalBeans.getBeanProvider(com.nanobaseai.actenora.security.microsoftconnection.GraphObservability.class),
                optionalBeans.getBeanProvider(
                        com.nanobaseai.actenora.security.microsoftconnection.TeamsTranscriptPollScheduler.class),
                optionalBeans.getBeanProvider(com.nanobaseai.actenora.notification.api.NotificationApi.class),
                optionalBeans.getBeanProvider(
                        com.nanobaseai.actenora.security.notification.PlatformUserNotificationPublisher.class),
                optionalBeans.getBeanProvider(com.nanobaseai.actenora.delivery.api.DeliveryApi.class),
                optionalBeans.getBeanProvider(com.nanobaseai.actenora.sharedkernel.port.storage.ObjectStorage.class),
                new PortalTeamsPreferencesStore(),
                "test-graph-client-id"
        );

        bind(tenantId, userId, Set.of(
                Permission.MEETING_READ.code(),
                Permission.MEETING_WRITE.code(),
                Permission.APPROVAL_DECIDE.code(),
                Permission.MODEL_CONTROL.code(),
                Permission.OPERATIONS_MANAGE.code(),
                Permission.AUDIT_READ.code(),
                Permission.TEMPLATE_MANAGE.code(),
                Permission.TENANT_READ.code(),
                Permission.TENANT_ADMINISTER.code()
        ), Set.of(SystemRole.TENANT_ADMIN.code()));
    }

    @AfterEach
    void tearDown() {
        TenantSecurityContext.clear();
    }

    @Test
    void meReturnsPortalPermissionsAndRole() {
        PortalApiController.PortalUserView me = controller.me();
        assertEquals(userId, me.id());
        assertEquals("ADMIN", me.role());
        assertTrue(me.permissions().contains("meetings:read"));
        assertTrue(me.permissions().contains("approvals:decide"));
        assertTrue(me.permissions().contains("operations:view"));
    }

    @Test
    void listMeetingsAndDashboardAgainstSeededMeetings() {
        var ctx = meetingApi.createBusinessContext(new CreateBusinessContextRequest(
                "PROJECT", "P1", "Context", "ctx"
        ));
        Instant start = Instant.parse("2026-07-20T09:00:00Z");
        MeetingResponse created = meetingApi.createMeeting(new CreateMeetingRequest(
                ctx.id(), null, null, "g1", "i1", start, null, null, null,
                "standup", MeetingType.STANDALONE, start, start.plusSeconds(3600),
                ProcessingPriority.NORMAL,
                List.of(new CreateMeetingRequest.ParticipantInput(
                        "oid", "Organizer", "org@example.com", "ORGANIZER", false
                ))
        ));

        PortalApiController.PortalCursorPage<PortalApiController.MeetingSummaryView> page =
                controller.listMeetings(null, null, 10, null);
        assertEquals(1, page.items().size());
        assertEquals(created.id(), page.items().get(0).id());
        assertEquals("standup", page.items().get(0).title());
        assertEquals(1, page.items().get(0).participantCount());

        PortalApiController.DashboardView dashboard = controller.dashboard();
        assertFalse(dashboard.recentMeetings().isEmpty());
        assertEquals(created.id(), dashboard.recentMeetings().get(0).id());

        PortalApiController.MeetingDetailView detail = controller.meetingDetail(created.id());
        assertEquals(created.id(), detail.meeting().id());
        assertEquals(1, detail.participants().size());
    }

    @Test
    void listAuditEventsReturnsTenantTrail() {
        UUID resourceId = UUID.randomUUID();
        auditApi.append(
                tenantId.value(),
                userId.toString(),
                "MEETING_CREATED",
                "Meeting",
                resourceId,
                Map.of("source", "test"),
                Instant.parse("2026-07-20T10:00:00Z")
        );

        PortalApiController.PortalCursorPage<PortalApiController.AuditEventView> page =
                controller.listAuditEvents(null, 10, null);
        assertEquals(1, page.items().size());
        assertEquals("MEETING_CREATED", page.items().get(0).action());
    }

    @Test
    void completeActionMarksLedgerItemCompleted() {
        var ctx = meetingApi.createBusinessContext(new CreateBusinessContextRequest(
                "PROJECT", "P1", "Context", "ctx"
        ));
        Instant start = Instant.parse("2026-07-20T09:00:00Z");
        MeetingResponse meeting = meetingApi.createMeeting(new CreateMeetingRequest(
                ctx.id(), null, null, "g1", "i1", start, null, null, null,
                "standup", MeetingType.STANDALONE, start, start.plusSeconds(3600),
                ProcessingPriority.NORMAL,
                List.of(new CreateMeetingRequest.ParticipantInput(
                        "oid", "Organizer", "org@example.com", "ORGANIZER", false
                ))
        ));
        UUID noteId = UUID.randomUUID();
        UUID actionId = ledgerService.recordActionItem(tenantId, meeting.id(), noteId, "Send recap");

        PortalApiController.ActionItemView completed = controller.completeAction(actionId);
        assertEquals("COMPLETED", completed.status());
        assertEquals(0, controller.dashboard().openActions());
    }

    @Test
    void listPendingApprovalsReturnsGroupedInbox() {
        UUID noteVersionId = UUID.randomUUID();
        approvalApi.openSingleStage(
                tenantId.value(),
                ApprovalSubjectType.MEETING_NOTE_VERSION,
                noteVersionId,
                userId.toString(),
                Instant.parse("2026-12-31T00:00:00Z")
        );

        PortalApiController.PendingApprovalsInboxView inbox = controller.listPendingApprovals();
        assertEquals(1, inbox.groups().size());
        assertEquals(1, inbox.groups().get(0).items().size());
        assertEquals("PENDING", inbox.groups().get(0).items().get(0).status());
    }

    @Test
    void dashboardCountsPendingApprovalsAndOpenActions() {
        var ctx = meetingApi.createBusinessContext(new CreateBusinessContextRequest(
                "PROJECT", "P1", "Context", "ctx"
        ));
        Instant start = Instant.parse("2026-07-20T09:00:00Z");
        MeetingResponse meeting = meetingApi.createMeeting(new CreateMeetingRequest(
                ctx.id(), null, null, "g1", "i1", start, null, null, null,
                "standup", MeetingType.STANDALONE, start, start.plusSeconds(3600),
                ProcessingPriority.NORMAL,
                List.of(new CreateMeetingRequest.ParticipantInput(
                        "oid", "Organizer", "org@example.com", "ORGANIZER", false
                ))
        ));
        UUID noteId = UUID.randomUUID();
        ledgerService.recordActionItem(tenantId, meeting.id(), noteId, "Follow up");
        approvalApi.openSingleStage(
                tenantId.value(),
                ApprovalSubjectType.MEETING_NOTE_VERSION,
                noteId,
                userId.toString(),
                Instant.parse("2026-12-31T00:00:00Z")
        );

        PortalApiController.DashboardView dashboard = controller.dashboard();
        assertEquals(1, dashboard.pendingApprovals());
        assertEquals(1, dashboard.openActions());
        assertFalse(dashboard.recentMeetings().isEmpty());
    }

    @Test
    void notificationsListMarkReadAndDedupe() {
        assertTrue(notificationApi.publish(
                tenantId,
                "local-oid",
                UserNotificationType.APPROVAL_REQUESTED,
                "Approval requested",
                "Waiting",
                "/approvals",
                "approval:portal-test"
        ));
        assertFalse(notificationApi.publish(
                tenantId,
                "local-oid",
                UserNotificationType.APPROVAL_REQUESTED,
                "Approval requested",
                "Waiting",
                "/approvals",
                "approval:portal-test"
        ));

        PortalApiController.PortalNotificationFeedView feed = controller.listNotifications(20);
        assertEquals(1, feed.items().size());
        assertEquals(1, feed.unreadCount());
        assertEquals("APPROVAL_REQUESTED", feed.items().getFirst().type());

        controller.markNotificationRead(feed.items().getFirst().id());
        assertEquals(0, controller.listNotifications(20).unreadCount());

        notificationApi.publish(
                tenantId,
                "local-oid",
                UserNotificationType.DRAFT_MINUTES_READY,
                "Draft minutes ready",
                "Ready",
                "/meetings/" + UUID.randomUUID(),
                "draft-minutes:portal-test"
        );
        assertEquals(1, controller.listNotifications(20).unreadCount());
        controller.markAllNotificationsRead();
        assertEquals(0, controller.listNotifications(20).unreadCount());
    }

    @Test
    void templatesListAndCreateAgainstTemplateModule() {
        PortalApiController.TemplateListView empty = controller.listTemplates(null);
        assertTrue(empty.items().isEmpty());

        PortalApiController.TemplateSummaryView created = controller.createTemplate(
                new PortalApiController.CreateTemplateBody("Executive summary", "en"));
        assertEquals("Executive summary", created.name());
        assertEquals("DRAFT", created.status());

        PortalApiController.TemplateListView listed = controller.listTemplates(null);
        assertEquals(1, listed.items().size());
        assertEquals(created.id(), listed.items().get(0).id());

        PortalApiController.TemplateDetailView detail = controller.getTemplate(created.id());
        assertEquals("Executive summary", detail.name());
        assertTrue(detail.versions().isEmpty());

        PortalApiController.TemplateVersionView draft = controller.createTemplateDraft(
                created.id(),
                new PortalApiController.CreateTemplateVersionBody("initial"));
        assertEquals("DRAFT", draft.status());
        assertEquals(1, draft.versionNumber());
    }

    @Test
    void defaultTemplateRequiresPublishedVersionAndPinsLatestPublished() {
        PortalApiController.TemplateSummaryView created = controller.createTemplate(
                new PortalApiController.CreateTemplateBody("Board minutes", "en"));
        assertFalse(created.isDefault());

        PortalApiController.TemplateVersionView draft = controller.createTemplateDraft(
                created.id(),
                new PortalApiController.CreateTemplateVersionBody("v1"));

        // A draft-only template cannot become the tenant default.
        assertThrows(TemplateDomainException.class, () -> controller.setDefaultTemplate(created.id()));

        controller.saveTemplateDesign(
                created.id(),
                draft.id(),
                new PortalApiController.SaveTemplateDesignBody(
                        designJson("EXECUTIVE_SUMMARY", "DECISIONS"), null));
        controller.publishTemplateVersion(created.id(), draft.id());

        PortalApiController.TemplateDetailView asDefault = controller.setDefaultTemplate(created.id());
        assertTrue(asDefault.isDefault());
        assertEquals(draft.id(), asDefault.publishedVersionId());
        assertTrue(controller.listTemplates(null).items().stream().allMatch(
                PortalApiController.TemplateSummaryView::isDefault));

        // Publishing v2 moves the default pointer for notes created from now on.
        PortalApiController.TemplateVersionView second = controller.createTemplateDraft(
                created.id(),
                new PortalApiController.CreateTemplateVersionBody("v2"));
        controller.saveTemplateDesign(
                created.id(),
                second.id(),
                new PortalApiController.SaveTemplateDesignBody(designJson("EXECUTIVE_SUMMARY"), null));
        controller.publishTemplateVersion(created.id(), second.id());

        PortalApiController.TemplateDetailView latest = controller.getTemplate(created.id());
        assertEquals(second.id(), latest.publishedVersionId());
        assertTrue(latest.isDefault());
    }

    @Test
    void onlyOneTemplateStaysDefaultPerTenant() {
        UUID first = publishedTemplate("Standard minutes");
        UUID second = publishedTemplate("Project minutes");

        controller.setDefaultTemplate(first);
        controller.setDefaultTemplate(second);

        List<PortalApiController.TemplateSummaryView> items = controller.listTemplates(null).items();
        assertEquals(1, items.stream().filter(PortalApiController.TemplateSummaryView::isDefault).count());
        assertTrue(items.stream()
                .filter(PortalApiController.TemplateSummaryView::isDefault)
                .allMatch(item -> item.id().equals(second)));
    }

    private UUID publishedTemplate(String name) {
        PortalApiController.TemplateSummaryView created = controller.createTemplate(
                new PortalApiController.CreateTemplateBody(name, "en"));
        PortalApiController.TemplateVersionView draft = controller.createTemplateDraft(
                created.id(), new PortalApiController.CreateTemplateVersionBody("v1"));
        controller.saveTemplateDesign(
                created.id(),
                draft.id(),
                new PortalApiController.SaveTemplateDesignBody(designJson("EXECUTIVE_SUMMARY"), null));
        controller.publishTemplateVersion(created.id(), draft.id());
        return created.id();
    }

    private static String designJson(String... types) {
        StringBuilder components = new StringBuilder();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) {
                components.append(',');
            }
            components.append("{\"id\":\"").append(UUID.randomUUID())
                    .append("\",\"type\":\"").append(types[i])
                    .append("\",\"order\":").append(i + 1)
                    .append(",\"props\":{}}");
        }
        return "{\"schemaVersion\":1,\"pageSize\":\"A4\",\"components\":[" + components + "]}";
    }

    @Test
    void teamsPreferencesPersistInPortalStore() {
        PortalApiController.TeamsSettingsView initial = controller.teamsSettings(null);
        assertFalse(initial.autoJoinEnabled());

        PortalApiController.TeamsSettingsView updated = controller.updateTeamsSettings(
                new PortalApiController.UpdateTeamsSettingsBody(true));
        assertTrue(updated.autoJoinEnabled());
    }

    @Test
    void permissionMapperCoversAdminSurface() {
        List<String> portal = PortalPermissionMapper.toPortalPermissions(Set.of(
                "MEETING_READ", "MEETING_WRITE", "APPROVAL_DECIDE", "MODEL_CONTROL",
                "OPERATIONS_MANAGE", "AUDIT_READ", "TEMPLATE_MANAGE", "TENANT_ADMINISTER"
        ));
        assertTrue(portal.contains("meetings:edit"));
        assertTrue(portal.contains("models:routing_detail"));
        assertTrue(portal.contains("teams:settings"));
        assertEquals("ADMIN", PortalPermissionMapper.toPortalRole(Set.of("TENANT_ADMIN")));
    }

    private void bind(TenantId tenant, UUID user, Set<String> permissions, Set<String> roles) {
        TenantSecurityContext.set(new AuthenticatedPrincipal(
                tenant,
                user,
                "local-oid",
                "user@example.com",
                "Test User",
                roles,
                permissions,
                true
        ));
    }

    private IdentityApi stubIdentityApi() {
        return new IdentityApi() {
            @Override
            public AuthenticatedPrincipal resolvePrincipal(TenantId tenantId, IdentityClaims claims) {
                throw new UnsupportedOperationException();
            }

            @Override
            public UserView currentUser(AuthenticatedPrincipal principal) {
                return new UserView(
                        principal.userId(),
                        principal.tenantId(),
                        principal.entraObjectId(),
                        principal.email(),
                        principal.displayName(),
                        UserStatus.ACTIVE,
                        Set.of(SystemRole.TENANT_ADMIN),
                        principal.permissions(),
                        Instant.parse("2026-07-01T00:00:00Z"),
                        Instant.parse("2026-07-01T00:00:00Z"),
                        1L
                );
            }

            @Override
            public List<UserView> listUsers(TenantId tenantId) {
                return List.of();
            }

            @Override
            public UserView grantRole(
                    TenantId tenantId, UUID uid, SystemRole role, long expectedVersion, UUID actorUserId
            ) {
                throw new UnsupportedOperationException();
            }

            @Override
            public UserView revokeRole(
                    TenantId tenantId, UUID uid, SystemRole role, long expectedVersion, UUID actorUserId
            ) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void requirePermission(AuthenticatedPrincipal principal, Permission permission) {
                if (!principal.permissions().contains(permission.code()) && !principal.globalAdmin()) {
                    throw new IllegalStateException("missing " + permission);
                }
            }

            @Override
            public Optional<UserView> findByEntraObjectId(String entraObjectId) {
                return Optional.empty();
            }

            @Override
            public Optional<UserView> findById(TenantId tenantId, UUID uid) {
                return Optional.empty();
            }
        };
    }
}
