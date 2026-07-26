package com.nanobaseai.actenora.security.meetingintelligence;

import com.nanobaseai.actenora.identity.api.IdentityApi;
import com.nanobaseai.actenora.identity.domain.AuthorizationDeniedException;
import com.nanobaseai.actenora.identity.api.Permission;
import com.nanobaseai.actenora.meetingintelligence.api.ledger.ContinuityLedgerApi;
import com.nanobaseai.actenora.meetingintelligence.application.ledger.ContinuityLedgerService;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContinuitySuggestionStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContradictionStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.model.CommitmentConfirmationStatus;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.ledger.InMemoryLedgerEventStore;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.ledger.InMemoryLedgerProjectionRepository;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.security.AuthenticatedPrincipal;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FAZ 21–25 — Continuity Ledger auth binding (suggestions, ops, rebuild, commitments).
 */
class ContinuityLedgerAuthBindingTest {

    private static final Instant NOW = Instant.parse("2026-07-25T23:00:00Z");

    private TenantId tenantId;
    private UUID userId;
    private ContinuityLedgerApi ledgerApi;
    private ContinuityLedgerAuthController controller;
    private InMemoryLedgerProjectionRepository projections;

    @BeforeEach
    void setUp() {
        tenantId = TenantId.random();
        userId = UUID.randomUUID();
        projections = new InMemoryLedgerProjectionRepository();
        ContinuityLedgerService service = new ContinuityLedgerService(
                new InMemoryLedgerEventStore(),
                projections,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        ledgerApi = new ContinuityLedgerApi(service);
        controller = new ContinuityLedgerAuthController(ledgerApi, stubIdentityApi());
    }

    @AfterEach
    void tearDown() {
        TenantSecurityContext.clear();
    }

    @Test
    void listAndAcceptSuggestionIsTenantScoped() {
        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        bind(tenantId, Set.of(Permission.MEETING_READ.code(), Permission.MEETING_WRITE.code()));

        var recorded = controller.recordSuggestion(new ContinuityLedgerAuthController.RecordSuggestionBody(
                source,
                target,
                "FOLLOW_UP",
                new BigDecimal("0.91"),
                "Agenda continuity"
        ));
        assertEquals(ContinuitySuggestionStatus.PENDING, recorded.status());
        assertEquals(1, controller.listSuggestions().size());
        assertEquals(1, controller.listEvents().size());

        var accepted = controller.acceptSuggestion(recorded.id());
        assertEquals(ContinuitySuggestionStatus.APPROVED, accepted.status());
        assertTrue(ledgerApi.continuity(tenantId, source).followUpChain().contains(target));
    }

    @Test
    void rejectSuggestionDoesNotLinkContinuity() {
        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        bind(tenantId, Set.of(Permission.MEETING_READ.code(), Permission.MEETING_WRITE.code()));

        var recorded = controller.recordSuggestion(new ContinuityLedgerAuthController.RecordSuggestionBody(
                source, target, "SAME_SERIES", new BigDecimal("0.40"), "Weak match"));
        var rejected = controller.rejectSuggestion(recorded.id());
        assertEquals(ContinuitySuggestionStatus.REJECTED, rejected.status());
        assertTrue(ledgerApi.continuity(tenantId, source).followUpChain().isEmpty());
    }

    @Test
    void contradictionsListAndPropose() {
        bind(tenantId, Set.of(Permission.MEETING_READ.code(), Permission.MEETING_WRITE.code()));
        UUID occurrence = UUID.randomUUID();
        UUID left = ledgerApi.recordDecision(tenantId, occurrence, UUID.randomUUID(), "Go remote").decisionId();
        UUID right = ledgerApi.recordDecision(tenantId, occurrence, UUID.randomUUID(), "Stay office").decisionId();

        var proposed = controller.proposeContradiction(new ContinuityLedgerAuthController.ProposeContradictionBody(
                occurrence, left, right, "Mutually exclusive", new BigDecimal("0.95")));
        assertEquals(ContradictionStatus.PENDING, proposed.status());
        assertEquals(1, controller.listContradictions().size());
        assertTrue(controller.listEvents().size() >= 3);

        var confirmed = controller.confirmContradiction(proposed.id());
        assertEquals(ContradictionStatus.CONFIRMED, confirmed.status());
    }

    @Test
    void rejectContradictionKeepsStatusRejected() {
        bind(tenantId, Set.of(Permission.MEETING_READ.code(), Permission.MEETING_WRITE.code()));
        UUID occurrence = UUID.randomUUID();
        UUID left = ledgerApi.recordDecision(tenantId, occurrence, UUID.randomUUID(), "A").decisionId();
        UUID right = ledgerApi.recordDecision(tenantId, occurrence, UUID.randomUUID(), "B").decisionId();
        var proposed = controller.proposeContradiction(new ContinuityLedgerAuthController.ProposeContradictionBody(
                occurrence, left, right, "noise", new BigDecimal("0.5")));
        assertEquals(ContradictionStatus.REJECTED, controller.rejectContradiction(proposed.id()).status());
    }

    @Test
    void overdueCommitmentsAndBriefAreReadable() {
        bind(tenantId, Set.of(Permission.MEETING_READ.code(), Permission.MEETING_WRITE.code()));
        UUID previous = UUID.randomUUID();
        UUID next = UUID.randomUUID();
        UUID series = UUID.randomUUID();
        ledgerApi.linkContinuity(tenantId, previous, series, null, null);
        ledgerApi.linkContinuity(tenantId, next, series, null, previous);
        ledgerApi.recordCommitment(
                tenantId,
                previous,
                UUID.randomUUID(),
                "Send report",
                "alice@example.com",
                LocalDate.parse("2026-07-20")
        );

        var overdue = controller.overdueCommitments();
        assertEquals(1, overdue.size());
        assertTrue(overdue.getFirst().overdue());

        var brief = controller.brief(next);
        assertEquals(next, brief.targetOccurrenceId());
        assertEquals(previous, brief.previousOccurrenceId());
        assertEquals(1, brief.overdueCommitments().size());

        var continuity = controller.continuity(next);
        assertEquals(previous, continuity.previousOccurrenceId());
        assertEquals(series, continuity.meetingSeriesId());
    }

    @Test
    void recordConfirmRejectCommitmentViaHttp() {
        bind(tenantId, Set.of(Permission.MEETING_READ.code(), Permission.MEETING_WRITE.code()));
        UUID occurrence = UUID.randomUUID();

        var recorded = controller.recordCommitment(new ContinuityLedgerAuthController.RecordCommitmentBody(
                occurrence,
                UUID.randomUUID(),
                "Send notes",
                "alice",
                LocalDate.parse("2026-07-20")
        ));
        assertEquals(CommitmentConfirmationStatus.PENDING_CONFIRMATION, recorded.status());
        assertTrue(recorded.overdue());
        assertEquals(1, controller.overdueCommitments().size());

        var confirmed = controller.confirmCommitment(recorded.commitmentId());
        assertEquals(CommitmentConfirmationStatus.CONFIRMED, confirmed.status());

        ActenoraException conflict = assertThrows(
                ActenoraException.class,
                () -> controller.rejectCommitment(recorded.commitmentId())
        );
        assertEquals("INVALID_COMMITMENT_TRANSITION", conflict.code());

        var other = controller.recordCommitment(new ContinuityLedgerAuthController.RecordCommitmentBody(
                occurrence, null, "Follow up", "bob", LocalDate.parse("2026-07-21")));
        assertEquals(CommitmentConfirmationStatus.REJECTED, controller.rejectCommitment(other.commitmentId()).status());
        assertTrue(controller.overdueCommitments().stream()
                .noneMatch(c -> c.commitmentId().equals(other.commitmentId())));
    }

    @Test
    void rebuildRestoresProjectionsFromEventStream() {
        bind(tenantId, Set.of(Permission.MEETING_READ.code(), Permission.MEETING_WRITE.code()));
        UUID occurrence = UUID.randomUUID();
        ledgerApi.recordDecision(tenantId, occurrence, UUID.randomUUID(), "Ship Friday");
        ledgerApi.recordCommitment(
                tenantId, occurrence, UUID.randomUUID(), "Send notes", "alice", LocalDate.parse("2026-07-20"));
        assertEquals(2, controller.listEvents().size());

        projections.getOrCreate(tenantId).clear();
        assertTrue(projections.getOrCreate(tenantId).decisions().isEmpty());
        assertTrue(controller.overdueCommitments().isEmpty());

        var rebuilt = controller.rebuild();
        assertEquals(2, rebuilt.eventCount());
        assertEquals(1, rebuilt.decisionCount());
        assertEquals(1, rebuilt.commitmentCount());
        assertEquals(1, controller.overdueCommitments().size());
    }

    @Test
    void foreignTenantSeesEmptyLists() {
        bind(tenantId, Set.of(Permission.MEETING_WRITE.code(), Permission.MEETING_READ.code()));
        controller.recordSuggestion(new ContinuityLedgerAuthController.RecordSuggestionBody(
                UUID.randomUUID(), UUID.randomUUID(), "FOLLOW_UP", new BigDecimal("0.8"), "x"));

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
        assertTrue(controller.listSuggestions().isEmpty());
        assertTrue(controller.listEvents().isEmpty());
        assertTrue(controller.listContradictions().isEmpty());
    }

    @Test
    void listDeniedWithoutPermission() {
        bind(tenantId, Set.of(Permission.MEETING_WRITE.code()));
        assertThrows(AuthorizationDeniedException.class, controller::listSuggestions);
    }

    private void bind(TenantId tenant, Set<String> permissions) {
        TenantSecurityContext.set(new AuthenticatedPrincipal(
                tenant,
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
            public List<com.nanobaseai.actenora.identity.api.UserView> listUsers(TenantId tenantId) {
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
