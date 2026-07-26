package com.nanobaseai.actenora.delivery.domain;

import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One mail to exactly one recipient for an approved note version.
 * Idempotent on (tenant, noteVersionId, recipient email).
 */
public final class DeliveryRequest {

    private final UUID id;
    private final TenantId tenantId;
    private final UUID noteVersionId;
    private final ApprovalId approvalId;
    private final DeliveryRecipient recipient;
    private final DeliveryPolicySnapshot policySnapshot;
    private DeliveryStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant nextAttemptAt;
    private final List<DeliveryAttempt> attempts;
    private PdfAttachmentDecision pdfAttachment;
    private SignedPortalLink signedPortalLink;
    private String subject;
    private String bodyText;
    private UUID deadLetterId;

    private DeliveryRequest(
            UUID id,
            TenantId tenantId,
            UUID noteVersionId,
            ApprovalId approvalId,
            DeliveryRecipient recipient,
            DeliveryPolicySnapshot policySnapshot,
            DeliveryStatus status,
            Instant createdAt,
            Instant updatedAt,
            Instant nextAttemptAt,
            List<DeliveryAttempt> attempts,
            PdfAttachmentDecision pdfAttachment,
            SignedPortalLink signedPortalLink,
            String subject,
            String bodyText,
            UUID deadLetterId
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.noteVersionId = Objects.requireNonNull(noteVersionId, "noteVersionId");
        this.approvalId = Objects.requireNonNull(approvalId, "approvalId");
        this.recipient = Objects.requireNonNull(recipient, "recipient");
        this.policySnapshot = Objects.requireNonNull(policySnapshot, "policySnapshot");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.nextAttemptAt = nextAttemptAt;
        this.attempts = new ArrayList<>(Objects.requireNonNull(attempts, "attempts"));
        this.pdfAttachment = pdfAttachment;
        this.signedPortalLink = signedPortalLink;
        this.subject = Objects.requireNonNull(subject, "subject");
        this.bodyText = Objects.requireNonNull(bodyText, "bodyText");
        this.deadLetterId = deadLetterId;
    }

    public static DeliveryRequest rehydrate(
            UUID id,
            TenantId tenantId,
            UUID noteVersionId,
            ApprovalId approvalId,
            DeliveryRecipient recipient,
            DeliveryPolicySnapshot policySnapshot,
            DeliveryStatus status,
            Instant createdAt,
            Instant updatedAt,
            Instant nextAttemptAt,
            List<DeliveryAttempt> attempts,
            PdfAttachmentDecision pdfAttachment,
            SignedPortalLink signedPortalLink,
            String subject,
            String bodyText,
            UUID deadLetterId
    ) {
        return new DeliveryRequest(
                id, tenantId, noteVersionId, approvalId, recipient, policySnapshot, status,
                createdAt, updatedAt, nextAttemptAt, attempts, pdfAttachment, signedPortalLink,
                subject, bodyText, deadLetterId
        );
    }

    public static DeliveryRequest enqueue(
            TenantId tenantId,
            UUID noteVersionId,
            ApprovalId approvalId,
            DeliveryRecipient recipient,
            DeliveryPolicySnapshot policySnapshot,
            String subject,
            String bodyText,
            Instant now
    ) {
        Objects.requireNonNull(policySnapshot, "policySnapshot");
        if (!policySnapshot.externalDeliveryAllowed() && recipient.isExternal()) {
            throw new DeliveryDomainException(
                    "EXTERNAL_DELIVERY_FORBIDDEN",
                    "external recipients are not allowed by policy");
        }
        if (policySnapshot.requireApproval() && approvalId == null) {
            throw new DeliveryDomainException("APPROVAL_REQUIRED", "approval id is required");
        }
        return new DeliveryRequest(
                UUID.randomUUID(),
                tenantId,
                noteVersionId,
                approvalId,
                recipient,
                policySnapshot,
                DeliveryStatus.QUEUED,
                now,
                now,
                now,
                List.of(),
                null,
                null,
                subject,
                bodyText,
                null
        );
    }

    public String idempotencyKey() {
        return recipient.idempotencyKey(noteVersionId);
    }

    public void applyPdfDecision(PdfAttachmentDecision decision) {
        if (decision.attach() && !policySnapshot.shouldAttachPdf()) {
            throw new DeliveryDomainException(
                    "PDF_ATTACHMENT_FORBIDDEN",
                    "policy forbids PDF attachment for this delivery");
        }
        this.pdfAttachment = Objects.requireNonNull(decision, "decision");
    }

    public void attachSignedPortalLink(SignedPortalLink link) {
        if (!policySnapshot.requireSignedPortalLink()) {
            throw new DeliveryDomainException(
                    "SIGNED_LINK_NOT_REQUIRED",
                    "signed portal link not required by policy");
        }
        this.signedPortalLink = Objects.requireNonNull(link, "link");
    }

    public DeliveryAttempt beginAttempt(Instant now) {
        if (status == DeliveryStatus.CANCELLED) {
            throw new DeliveryDomainException("DELIVERY_CANCELLED", "request was cancelled");
        }
        if (status.isTerminal() && status != DeliveryStatus.FAILED && status != DeliveryStatus.DEFERRED) {
            throw new DeliveryDomainException("DELIVERY_TERMINAL", "request is already terminal: " + status);
        }
        if (attempts.size() >= policySnapshot.maxAttempts()) {
            throw new DeliveryDomainException("MAX_ATTEMPTS_EXCEEDED", "max delivery attempts exceeded");
        }
        DeliveryAttempt attempt = DeliveryAttempt.start(attempts.size() + 1, now);
        attempts.add(attempt);
        status = DeliveryStatus.SENDING;
        updatedAt = now;
        nextAttemptAt = null;
        return attempt;
    }

    public void completeProviderAccepted(DeliveryAttempt attempt, ProviderMessage message, Instant now) {
        attempt.markProviderAccepted(message, now);
        status = DeliveryStatus.PROVIDER_ACCEPTED;
        updatedAt = now;
    }

    public void completeDelivered(DeliveryAttempt attempt, Instant now) {
        attempt.markDelivered(now);
        status = DeliveryStatus.DELIVERED;
        updatedAt = now;
        nextAttemptAt = null;
    }

    public void completeDeferred(DeliveryAttempt attempt, String code, String detail, Instant nextAt, Instant now) {
        attempt.markDeferred(code, detail, now);
        status = DeliveryStatus.DEFERRED;
        nextAttemptAt = nextAt;
        updatedAt = now;
    }

    public void completeBounced(DeliveryAttempt attempt, String code, String detail, Instant now) {
        attempt.markBounced(code, detail, now);
        status = DeliveryStatus.BOUNCED;
        updatedAt = now;
        nextAttemptAt = null;
    }

    public void completeFailed(DeliveryAttempt attempt, String code, String detail, Instant now) {
        attempt.markFailed(code, detail, now);
        status = DeliveryStatus.FAILED;
        updatedAt = now;
    }

    public void scheduleRetry(Instant nextAt, Instant now) {
        if (!status.allowsRetry()) {
            throw new DeliveryDomainException("RETRY_NOT_ALLOWED", "status does not allow retry: " + status);
        }
        status = DeliveryStatus.QUEUED;
        nextAttemptAt = nextAt;
        updatedAt = now;
    }

    /**
     * Postpones a queued send without consuming an attempt (e.g. rate limit).
     */
    public void postpone(Instant nextAt, Instant now) {
        if (status != DeliveryStatus.QUEUED && status != DeliveryStatus.DEFERRED) {
            throw new DeliveryDomainException(
                    "POSTPONE_NOT_ALLOWED",
                    "can only postpone queued/deferred requests, was " + status);
        }
        status = DeliveryStatus.QUEUED;
        nextAttemptAt = Objects.requireNonNull(nextAt, "nextAt");
        updatedAt = now;
    }

    public void cancel(Instant now) {
        if (status.isTerminal()) {
            throw new DeliveryDomainException("DELIVERY_TERMINAL", "cannot cancel terminal request");
        }
        status = DeliveryStatus.CANCELLED;
        updatedAt = now;
        nextAttemptAt = null;
    }

    public void markDeadLetter(UUID deadLetterId, Instant now) {
        this.deadLetterId = Objects.requireNonNull(deadLetterId, "deadLetterId");
        this.status = DeliveryStatus.FAILED;
        this.updatedAt = now;
        this.nextAttemptAt = null;
    }

    public boolean isDue(Instant now) {
        return status == DeliveryStatus.QUEUED
                && (nextAttemptAt == null || !now.isBefore(nextAttemptAt));
    }

    public boolean attemptsExhausted() {
        return attempts.size() >= policySnapshot.maxAttempts();
    }

    public UUID id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public UUID noteVersionId() {
        return noteVersionId;
    }

    public ApprovalId approvalId() {
        return approvalId;
    }

    public DeliveryRecipient recipient() {
        return recipient;
    }

    public DeliveryPolicySnapshot policySnapshot() {
        return policySnapshot;
    }

    public DeliveryStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Optional<Instant> nextAttemptAt() {
        return Optional.ofNullable(nextAttemptAt);
    }

    public List<DeliveryAttempt> attempts() {
        return Collections.unmodifiableList(attempts);
    }

    public Optional<PdfAttachmentDecision> pdfAttachment() {
        return Optional.ofNullable(pdfAttachment);
    }

    public Optional<SignedPortalLink> signedPortalLink() {
        return Optional.ofNullable(signedPortalLink);
    }

    public String subject() {
        return subject;
    }

    public String bodyText() {
        return bodyText;
    }

    public Optional<UUID> deadLetterId() {
        return Optional.ofNullable(deadLetterId);
    }

    public Optional<DeliveryAttempt> latestAttempt() {
        if (attempts.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(attempts.get(attempts.size() - 1));
    }
}
