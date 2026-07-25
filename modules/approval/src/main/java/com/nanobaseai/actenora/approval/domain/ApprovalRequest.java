package com.nanobaseai.actenora.approval.domain;

import com.nanobaseai.actenora.approval.api.ApprovalDecisionType;
import com.nanobaseai.actenora.approval.api.ApprovalRequestStatus;
import com.nanobaseai.actenora.approval.api.ApprovalSubjectType;

import com.nanobaseai.actenora.approval.domain.exception.InvalidApprovalTransitionException;
import com.nanobaseai.actenora.approval.domain.exception.OptimisticLockConflictException;
import com.nanobaseai.actenora.approval.domain.exception.UnauthorizedApprovalException;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Approval request aggregate. Supports multi-step chains; V1 opens a single pending step.
 * Delivery consumers must use {@link com.nanobaseai.actenora.approval.api.ApprovalId} only.
 */
public final class ApprovalRequest {

    private final UUID id;
    private final TenantId tenantId;
    private final ApprovalSubjectType subjectType;
    private final UUID subjectId;
    private ApprovalRequestStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant expiresAt;
    private long version;
    private final List<ApprovalStep> steps;
    private final List<ApprovalDecision> decisions;
    private final List<ChangeRequest> changeRequests;

    private ApprovalRequest(
            UUID id,
            TenantId tenantId,
            ApprovalSubjectType subjectType,
            UUID subjectId,
            ApprovalRequestStatus status,
            Instant createdAt,
            Instant updatedAt,
            Instant expiresAt,
            long version,
            List<ApprovalStep> steps,
            List<ApprovalDecision> decisions,
            List<ChangeRequest> changeRequests
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.subjectType = Objects.requireNonNull(subjectType, "subjectType");
        this.subjectId = Objects.requireNonNull(subjectId, "subjectId");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.expiresAt = expiresAt;
        this.version = version;
        this.steps = new ArrayList<>(Objects.requireNonNull(steps, "steps"));
        this.decisions = new ArrayList<>(Objects.requireNonNull(decisions, "decisions"));
        this.changeRequests = new ArrayList<>(Objects.requireNonNull(changeRequests, "changeRequests"));
        if (this.steps.isEmpty()) {
            throw new IllegalArgumentException("approval request requires at least one step");
        }
    }

    /**
     * Opens a single-stage approval (V1). Pass multiple approver ids to prepare multi-stage data.
     */
    public static ApprovalRequest openSingleStage(
            TenantId tenantId,
            ApprovalSubjectType subjectType,
            UUID subjectId,
            String requiredApproverId,
            Instant expiresAt,
            Instant now
    ) {
        return open(tenantId, subjectType, subjectId, List.of(requiredApproverId), expiresAt, now);
    }

    /**
     * Opens an ordered multi-stage approval. Only step 1 is active until prior steps are granted.
     */
    public static ApprovalRequest open(
            TenantId tenantId,
            ApprovalSubjectType subjectType,
            UUID subjectId,
            List<String> orderedApproverIds,
            Instant expiresAt,
            Instant now
    ) {
        Objects.requireNonNull(orderedApproverIds, "orderedApproverIds");
        if (orderedApproverIds.isEmpty()) {
            throw new IllegalArgumentException("at least one approver is required");
        }
        List<ApprovalStep> steps = new ArrayList<>();
        int order = 1;
        for (String approverId : orderedApproverIds) {
            steps.add(ApprovalStep.pending(order++, approverId));
        }
        return new ApprovalRequest(
                UUID.randomUUID(),
                tenantId,
                subjectType,
                subjectId,
                ApprovalRequestStatus.PENDING,
                now,
                now,
                expiresAt,
                0L,
                steps,
                List.of(),
                List.of()
        );
    }

    public static ApprovalRequest rehydrate(
            UUID id,
            TenantId tenantId,
            ApprovalSubjectType subjectType,
            UUID subjectId,
            ApprovalRequestStatus status,
            Instant createdAt,
            Instant updatedAt,
            Instant expiresAt,
            long version,
            List<ApprovalStep> steps,
            List<ApprovalDecision> decisions,
            List<ChangeRequest> changeRequests
    ) {
        return new ApprovalRequest(
                id, tenantId, subjectType, subjectId, status, createdAt, updatedAt, expiresAt, version,
                steps, decisions, changeRequests
        );
    }

    public ApprovalDecision decide(
            String actorId,
            ApprovalDecisionType decisionType,
            String comment,
            Instant now
    ) {
        requireNotExpired(now);
        if (status != ApprovalRequestStatus.PENDING) {
            throw new InvalidApprovalTransitionException(
                    "approval is " + status + " and cannot accept decisions"
            );
        }
        ApprovalStep current = currentPendingStep()
                .orElseThrow(() -> new InvalidApprovalTransitionException("no pending approval step"));
        if (!current.isAuthorized(actorId)) {
            throw new UnauthorizedApprovalException(id, actorId);
        }

        ApprovalDecision decision = ApprovalDecision.record(
                id, current.id(), decisionType, actorId, comment, now
        );
        decisions.add(decision);

        return switch (decisionType) {
            case APPROVE -> applyApprove(current, decision, now);
            case REJECT -> applyReject(current, decision, now);
            case REQUEST_CHANGES -> applyChangesRequested(current, decision, now);
        };
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    public void expireIfDue(Instant now) {
        if (status == ApprovalRequestStatus.PENDING && isExpired(now)) {
            ApprovalStateMachine.assertTransition(status, ApprovalRequestStatus.EXPIRED);
            status = ApprovalRequestStatus.EXPIRED;
            updatedAt = now;
            version++;
        }
    }

    public boolean isGranted() {
        return status == ApprovalRequestStatus.GRANTED;
    }

    public void assertVersion(long expectedVersion) {
        if (this.version != expectedVersion) {
            throw new OptimisticLockConflictException(id, expectedVersion);
        }
    }

    public Optional<ApprovalStep> currentPendingStep() {
        return steps.stream()
                .filter(ApprovalStep::isPending)
                .min(Comparator.comparingInt(ApprovalStep::stepOrder));
    }

    private ApprovalDecision applyApprove(ApprovalStep current, ApprovalDecision decision, Instant now) {
        replaceStep(current.markGranted(now));
        boolean allGranted = steps.stream().allMatch(s -> s.status() == ApprovalStepStatus.GRANTED);
        if (allGranted) {
            ApprovalStateMachine.assertTransition(status, ApprovalRequestStatus.GRANTED);
            status = ApprovalRequestStatus.GRANTED;
        }
        updatedAt = now;
        version++;
        return decision;
    }

    private ApprovalDecision applyReject(ApprovalStep current, ApprovalDecision decision, Instant now) {
        replaceStep(current.markDenied(now));
        ApprovalStateMachine.assertTransition(status, ApprovalRequestStatus.DENIED);
        status = ApprovalRequestStatus.DENIED;
        updatedAt = now;
        version++;
        return decision;
    }

    private ApprovalDecision applyChangesRequested(ApprovalStep current, ApprovalDecision decision, Instant now) {
        // Step stays pending conceptually at request level; request moves to CHANGES_REQUESTED.
        ApprovalStateMachine.assertTransition(status, ApprovalRequestStatus.CHANGES_REQUESTED);
        status = ApprovalRequestStatus.CHANGES_REQUESTED;
        String reason = decision.comment().isBlank() ? "Changes requested" : decision.comment();
        changeRequests.add(ChangeRequest.open(id, subjectId, decision.decidedBy(), reason, now));
        updatedAt = now;
        version++;
        return decision;
    }

    private void replaceStep(ApprovalStep updated) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).id().equals(updated.id())) {
                steps.set(i, updated);
                return;
            }
        }
        throw new IllegalStateException("step not found: " + updated.id());
    }

    private void requireNotExpired(Instant now) {
        if (isExpired(now)) {
            expireIfDue(now);
            throw new InvalidApprovalTransitionException("approval request has expired");
        }
    }

    public UUID id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public ApprovalSubjectType subjectType() {
        return subjectType;
    }

    public UUID subjectId() {
        return subjectId;
    }

    public ApprovalRequestStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public long version() {
        return version;
    }

    public List<ApprovalStep> steps() {
        return Collections.unmodifiableList(steps);
    }

    public List<ApprovalDecision> decisions() {
        return Collections.unmodifiableList(decisions);
    }

    public List<ChangeRequest> changeRequests() {
        return Collections.unmodifiableList(changeRequests);
    }
}
