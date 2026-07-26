package com.nanobaseai.actenora.security.portal;

import com.nanobaseai.actenora.approval.api.ApprovalApi;
import com.nanobaseai.actenora.approval.application.ApprovalWorkflowService;
import com.nanobaseai.actenora.approval.infrastructure.ApprovalApiAdapter;
import com.nanobaseai.actenora.approval.infrastructure.InMemoryApprovalRequestRepository;
import com.nanobaseai.actenora.approval.infrastructure.InMemoryParticipantDisputeRepository;
import com.nanobaseai.actenora.approval.infrastructure.RecordingApprovalAuditPort;
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
import com.nanobaseai.actenora.meetingintelligence.application.ledger.ContinuityLedgerService;
import com.nanobaseai.actenora.meetingintelligence.application.port.ApprovedNoteLedgerPort;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.RecordingMeetingIntelligenceAuditPort;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.ledger.InMemoryLedgerEventStore;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.ledger.InMemoryLedgerProjectionRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryMeetingNoteRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryMeetingNoteVersionRepository;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.security.AuthenticatedPrincipal;
import com.nanobaseai.actenora.sharedkernel.security.IdentityClaims;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FAZ 33 — portal BFF returns portal-shaped DTOs for me / dashboard / meetings.
 */
class PortalApiBindingTest {

    private TenantId tenantId;
    private UUID userId;
    private PortalApiController controller;
    private MeetingApi meetingApi;

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

        ContinuityLedgerApi ledgerApi = new ContinuityLedgerApi(new ContinuityLedgerService(
                new InMemoryLedgerEventStore(),
                new InMemoryLedgerProjectionRepository(),
                Clock.systemUTC()
        ));

        ApprovalApi approvalApi = new ApprovalApiAdapter(
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

        controller = new PortalApiController(
                stubIdentityApi(),
                meetingApi,
                ledgerApi,
                approvalApi,
                noteApproval,
                intelligenceProvider,
                operationsProvider
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
                "PROJECT", "P1", "Demo", "demo"
        ));
        Instant start = Instant.parse("2026-07-20T09:00:00Z");
        MeetingResponse created = meetingApi.createMeeting(new CreateMeetingRequest(
                ctx.id(), null, null, "g1", "i1", start, null, null, null,
                "Q3 roadmap sync", MeetingType.STANDALONE, start, start.plusSeconds(3600),
                ProcessingPriority.NORMAL,
                List.of(new CreateMeetingRequest.ParticipantInput(
                        "oid", "Ada", "ada@x.com", "ORGANIZER", false
                ))
        ));

        PortalApiController.PortalCursorPage<PortalApiController.MeetingSummaryView> page =
                controller.listMeetings(null, null, 10, null);
        assertEquals(1, page.items().size());
        assertEquals(created.id(), page.items().get(0).id());
        assertEquals("Q3 roadmap sync", page.items().get(0).title());
        assertEquals(1, page.items().get(0).participantCount());

        PortalApiController.DashboardView dashboard = controller.dashboard();
        assertFalse(dashboard.recentMeetings().isEmpty());
        assertEquals(created.id(), dashboard.recentMeetings().get(0).id());

        PortalApiController.MeetingDetailView detail = controller.meetingDetail(created.id());
        assertEquals(created.id(), detail.meeting().id());
        assertEquals(1, detail.participants().size());
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
                "local-oid-admin",
                "ada@actenora.local",
                "Ada Admin",
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
