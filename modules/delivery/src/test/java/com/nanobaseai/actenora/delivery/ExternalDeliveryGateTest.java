package com.nanobaseai.actenora.delivery;

import com.nanobaseai.actenora.approval.api.ApprovalApi;
import com.nanobaseai.actenora.approval.api.ApprovalDecisionType;
import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.approval.api.ApprovalRequestStatus;
import com.nanobaseai.actenora.approval.api.ApprovalSubjectType;
import com.nanobaseai.actenora.delivery.application.ExternalDeliveryService;
import com.nanobaseai.actenora.delivery.domain.DeliveryOrderStatus;
import com.nanobaseai.actenora.delivery.domain.exception.ExternalDeliveryBlockedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExternalDeliveryGateTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-25T12:00:00Z"), ZoneOffset.UTC);

    private UUID tenantId;
    private StubApprovalApi approvalApi;
    private ExternalDeliveryService delivery;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        approvalApi = new StubApprovalApi();
        delivery = new ExternalDeliveryService(approvalApi, CLOCK);
    }

    @Test
    void externalDeliveryBlockedUntilGrantedForVersion() {
        UUID versionId = UUID.randomUUID();
        ApprovalId approvalId = ApprovalId.of(UUID.randomUUID());
        approvalApi.put(approvalId, versionId, false);

        assertThrows(ExternalDeliveryBlockedException.class, () ->
                delivery.requestExternalDelivery(tenantId, approvalId, versionId, "email")
        );

        approvalApi.put(approvalId, versionId, true);
        var order = delivery.requestExternalDelivery(tenantId, approvalId, versionId, "email");
        assertEquals(DeliveryOrderStatus.READY, order.status());

        var again = delivery.requestExternalDelivery(tenantId, approvalId, versionId, "email");
        assertEquals(order.id(), again.id());

        assertThrows(ExternalDeliveryBlockedException.class, () ->
                delivery.requestExternalDelivery(tenantId, approvalId, UUID.randomUUID(), "email")
        );
    }

    private static final class StubApprovalApi implements ApprovalApi {
        private final Map<UUID, Entry> byId = new ConcurrentHashMap<>();

        void put(ApprovalId id, UUID subjectId, boolean granted) {
            byId.put(id.value(), new Entry(subjectId, granted));
        }

        @Override
        public ApprovalId openSingleStage(
                UUID tenantId, ApprovalSubjectType subjectType, UUID subjectId,
                String approverId, Instant expiresAt
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ApprovalId open(
                UUID tenantId, ApprovalSubjectType subjectType, UUID subjectId,
                List<String> orderedApproverIds, Instant expiresAt
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ApprovalRequestStatus decide(
                UUID tenantId, ApprovalId approvalId, String actorId,
                ApprovalDecisionType decisionType, String comment, long expectedVersion
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isGranted(UUID tenantId, ApprovalId approvalId) {
            Entry e = byId.get(approvalId.value());
            return e != null && e.granted;
        }

        @Override
        public boolean isGrantedForSubject(UUID tenantId, ApprovalId approvalId, UUID subjectId) {
            Entry e = byId.get(approvalId.value());
            return e != null && e.granted && e.subjectId.equals(subjectId);
        }

        @Override
        public Optional<ApprovalRequestStatus> status(UUID tenantId, ApprovalId approvalId) {
            return Optional.empty();
        }

        @Override
        public Optional<Long> version(UUID tenantId, ApprovalId approvalId) {
            return Optional.empty();
        }

        @Override
        public Optional<com.nanobaseai.actenora.approval.api.ApprovalRequestView> get(
                UUID tenantId, ApprovalId approvalId) {
            return Optional.empty();
        }

        @Override
        public Optional<ApprovalId> findBySubject(UUID tenantId, UUID subjectId) {
            return Optional.empty();
        }

        @Override
        public List<com.nanobaseai.actenora.approval.api.ApprovalRequestView> listForTenant(UUID tenantId) {
            return List.of();
        }

        @Override
        public UUID raiseDispute(
                UUID tenantId, UUID subjectId, ApprovalSubjectType subjectType,
                String participantId, String proposedContent, String reason
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String acceptDispute(UUID tenantId, UUID disputeId, String resolverId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void rejectDispute(UUID tenantId, UUID disputeId, String resolverId) {
            throw new UnsupportedOperationException();
        }

        private record Entry(UUID subjectId, boolean granted) {
        }
    }
}
