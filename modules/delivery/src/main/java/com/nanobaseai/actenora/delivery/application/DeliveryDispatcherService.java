package com.nanobaseai.actenora.delivery.application;

import com.nanobaseai.actenora.delivery.api.DeliveryRequestId;
import com.nanobaseai.actenora.delivery.application.port.DeliveryAuditPort;
import com.nanobaseai.actenora.delivery.application.port.DeliveryMailProvider;
import com.nanobaseai.actenora.delivery.application.port.DeliveryRateLimiter;
import com.nanobaseai.actenora.delivery.application.port.DeliveryRequestRepository;
import com.nanobaseai.actenora.delivery.application.port.NoteApprovalGatePort;
import com.nanobaseai.actenora.delivery.application.port.PdfAttachmentPort;
import com.nanobaseai.actenora.delivery.application.port.SignedPortalLinkPort;
import com.nanobaseai.actenora.delivery.domain.DeliveryAttempt;
import com.nanobaseai.actenora.delivery.domain.DeliveryDeadLetter;
import com.nanobaseai.actenora.delivery.domain.DeliveryDomainException;
import com.nanobaseai.actenora.delivery.domain.DeliveryIntent;
import com.nanobaseai.actenora.delivery.domain.DeliveryRequest;
import com.nanobaseai.actenora.delivery.domain.DeliveryStatus;
import com.nanobaseai.actenora.delivery.domain.PdfAttachmentDecision;
import com.nanobaseai.actenora.delivery.domain.ProviderMessage;
import com.nanobaseai.actenora.delivery.domain.SignedPortalLink;
import com.nanobaseai.actenora.sharedkernel.messaging.ExponentialBackoff;
import com.nanobaseai.actenora.sharedkernel.messaging.RetryClassification;
import com.nanobaseai.actenora.sharedkernel.messaging.RetryClassifier;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Enqueues approved-note deliveries and drives send attempts with rate limit, retry, and DLQ.
 */
public final class DeliveryDispatcherService {

    public static final String ACTOR_SYSTEM = "delivery-worker";

    private final DeliveryRequestRepository repository;
    private final NoteApprovalGatePort approvalGate;
    private final DeliveryMailProvider mailProvider;
    private final DeliveryRateLimiter rateLimiter;
    private final PdfAttachmentPort pdfAttachmentPort;
    private final SignedPortalLinkPort signedPortalLinkPort;
    private final DeliveryAuditPort auditPort;
    private final InstantClock clock;
    private final ExponentialBackoff backoff;
    private final RetryClassifier retryClassifier;
    private final Duration providerTimeout;

    public DeliveryDispatcherService(
            DeliveryRequestRepository repository,
            NoteApprovalGatePort approvalGate,
            DeliveryMailProvider mailProvider,
            DeliveryRateLimiter rateLimiter,
            PdfAttachmentPort pdfAttachmentPort,
            SignedPortalLinkPort signedPortalLinkPort,
            DeliveryAuditPort auditPort,
            InstantClock clock
    ) {
        this(
                repository,
                approvalGate,
                mailProvider,
                rateLimiter,
                pdfAttachmentPort,
                signedPortalLinkPort,
                auditPort,
                clock,
                ExponentialBackoff.defaults(),
                new RetryClassifier.Default(),
                Duration.ofSeconds(30)
        );
    }

    public DeliveryDispatcherService(
            DeliveryRequestRepository repository,
            NoteApprovalGatePort approvalGate,
            DeliveryMailProvider mailProvider,
            DeliveryRateLimiter rateLimiter,
            PdfAttachmentPort pdfAttachmentPort,
            SignedPortalLinkPort signedPortalLinkPort,
            DeliveryAuditPort auditPort,
            InstantClock clock,
            ExponentialBackoff backoff,
            RetryClassifier retryClassifier,
            Duration providerTimeout
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.approvalGate = Objects.requireNonNull(approvalGate, "approvalGate");
        this.mailProvider = Objects.requireNonNull(mailProvider, "mailProvider");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.pdfAttachmentPort = Objects.requireNonNull(pdfAttachmentPort, "pdfAttachmentPort");
        this.signedPortalLinkPort = Objects.requireNonNull(signedPortalLinkPort, "signedPortalLinkPort");
        this.auditPort = Objects.requireNonNull(auditPort, "auditPort");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.backoff = Objects.requireNonNull(backoff, "backoff");
        this.retryClassifier = Objects.requireNonNull(retryClassifier, "retryClassifier");
        this.providerTimeout = Objects.requireNonNull(providerTimeout, "providerTimeout");
    }

    public EnqueueDeliveryResult enqueue(EnqueueDeliveryCommand command) {
        Instant now = clock.now();
        if (command.policySnapshot().requireApproval()) {
            boolean approved = approvalGate.isNoteVersionApproved(
                    command.tenantId(),
                    command.approvalId(),
                    command.noteVersionId()
            );
            if (!approved) {
                throw new DeliveryDomainException(
                        "NOTE_NOT_APPROVED",
                        "unapproved note versions cannot be delivered");
            }
        }

        List<DeliveryRequestId> created = new ArrayList<>();
        List<DeliveryRequestId> duplicates = new ArrayList<>();

        for (var recipient : command.recipients()) {
            String intent = command.policySnapshot().requireApproval()
                    ? DeliveryIntent.FINAL_EXTERNAL
                    : DeliveryIntent.DRAFT_ORGANIZER;
            Optional<DeliveryRequest> existing = repository.findByNoteVersionRecipientAndIntent(
                    command.tenantId(),
                    command.noteVersionId(),
                    recipient.email(),
                    intent
            );
            if (existing.isPresent()) {
                duplicates.add(DeliveryRequestId.of(existing.get().id()));
                auditPort.record(
                        command.tenantId().value(),
                        command.actorId(),
                        "DELIVERY_DUPLICATE_SUPPRESSED",
                        "DeliveryRequest",
                        existing.get().id(),
                        Map.of(
                                "noteVersionId", command.noteVersionId().toString(),
                                "recipient", recipient.email(),
                                "kind", recipient.kind().name()
                        ),
                        now
                );
                continue;
            }

            DeliveryRequest request = DeliveryRequest.enqueue(
                    command.tenantId(),
                    command.noteVersionId(),
                    command.approvalId(),
                    recipient,
                    command.policySnapshot(),
                    command.subject(),
                    command.bodyText(),
                    now
            );

            PdfAttachmentDecision pdfDecision = pdfAttachmentPort.decide(
                    command.tenantId(),
                    command.noteVersionId(),
                    command.policySnapshot()
            );
            request.applyPdfDecision(pdfDecision);

            if (command.policySnapshot().requireSignedPortalLink()) {
                SignedPortalLink link = signedPortalLinkPort.issue(
                        command.tenantId(),
                        command.noteVersionId(),
                        request.id(),
                        recipient.email(),
                        command.policySnapshot().signedLinkTtl(),
                        now
                );
                request.attachSignedPortalLink(link);
            }

            DeliveryRequest saved = repository.save(request);
            created.add(DeliveryRequestId.of(saved.id()));
            auditPort.record(
                    command.tenantId().value(),
                    command.actorId(),
                    "DELIVERY_QUEUED",
                    "DeliveryRequest",
                    saved.id(),
                    Map.of(
                            "noteVersionId", saved.noteVersionId().toString(),
                            "approvalId", saved.approvalId().value().toString(),
                            "recipient", saved.recipient().email(),
                            "kind", saved.recipient().kind().name(),
                            "providerType", saved.policySnapshot().providerType(),
                            "attachPdf", String.valueOf(pdfDecision.attach()),
                            "sensitive", String.valueOf(saved.policySnapshot().sensitiveMeeting())
                    ),
                    now
            );
        }

        return new EnqueueDeliveryResult(created, duplicates);
    }

    /**
     * Processes one due request. Safe to call from a separately deployable delivery-worker.
     */
    public DeliveryStatus processNext(DeliveryRequest request) {
        Instant now = clock.now();
        if (!request.isDue(now)) {
            return request.status();
        }

        String providerType = request.policySnapshot().providerType();
        if (!rateLimiter.tryAcquire(request.tenantId(), providerType)) {
            Instant deferredUntil = now.plus(Duration.ofSeconds(5));
            request.postpone(deferredUntil, now);
            repository.save(request);
            auditPort.record(
                    request.tenantId().value(),
                    ACTOR_SYSTEM,
                    "DELIVERY_RATE_LIMITED",
                    "DeliveryRequest",
                    request.id(),
                    Map.of("providerType", providerType),
                    now
            );
            return DeliveryStatus.DEFERRED;
        }

        try {
            return doSend(request, now);
        } finally {
            rateLimiter.release(request.tenantId(), providerType);
        }
    }

    private DeliveryStatus doSend(DeliveryRequest request, Instant now) {
        if (request.attemptsExhausted()) {
            return deadLetter(request, "MAX_ATTEMPTS_EXCEEDED", "max attempts exhausted", now);
        }

        DeliveryAttempt attempt = request.beginAttempt(now);
        Optional<byte[]> pdfBytes = request.pdfAttachment()
                .filter(PdfAttachmentDecision::attach)
                .flatMap(d -> pdfAttachmentPort.loadPdfBytes(request.tenantId(), d));

        Optional<String> portalUrl = Optional.empty();
        if (request.signedPortalLink().isPresent()) {
            SignedPortalLink link = request.signedPortalLink().orElseThrow();
            if (link.isExpired(now)) {
                return handleFailure(request, attempt, "SIGNED_LINK_EXPIRED", "signed portal link expired before send", now);
            }
            if (!signedPortalLinkPort.isValid(link, now)) {
                return handleFailure(request, attempt, "SIGNED_LINK_INVALID", "signed portal link failed validation", now);
            }
            portalUrl = Optional.of(link.url().toString());
        }

        DeliveryMailProvider.SendResult result;
        try {
            result = mailProvider.send(new DeliveryMailProvider.SendCommand(
                    request,
                    pdfBytes,
                    portalUrl,
                    providerTimeout
            ));
        } catch (DeliveryDomainException ex) {
            return handleFailure(request, attempt, ex.code(), ex.getMessage(), now);
        } catch (RuntimeException ex) {
            return handleFailure(request, attempt, "PROVIDER_ERROR", ex.getMessage(), now);
        }

        return switch (result.outcome()) {
            case DELIVERED -> {
                ProviderMessage message = result.providerMessage();
                if (message == null) {
                    message = ProviderMessage.accepted(
                            mailProvider.providerType(),
                            "local-" + attempt.id(),
                            now
                    );
                }
                request.completeProviderAccepted(attempt, message, now);
                // Provider accepted first, then promote to delivered explicitly.
                request.completeDelivered(attempt, now);
                repository.save(request);
                auditSuccess(request, "DELIVERY_DELIVERED", now);
                yield DeliveryStatus.DELIVERED;
            }
            case PROVIDER_ACCEPTED -> {
                request.completeProviderAccepted(attempt, result.providerMessage(), now);
                repository.save(request);
                auditSuccess(request, "DELIVERY_PROVIDER_ACCEPTED", now);
                // Explicit invariant: accepted ≠ delivered
                yield DeliveryStatus.PROVIDER_ACCEPTED;
            }
            case DEFERRED -> {
                Instant next = now.plus(backoff.delayForAttempt(attempt.attemptNumber()));
                request.completeDeferred(
                        attempt,
                        result.failureCode(),
                        result.failureDetail(),
                        next,
                        now
                );
                request.scheduleRetry(next, now);
                repository.save(request);
                auditFailure(request, "DELIVERY_DEFERRED", result.failureCode(), now);
                yield DeliveryStatus.DEFERRED;
            }
            case BOUNCED -> {
                request.completeBounced(attempt, result.failureCode(), result.failureDetail(), now);
                repository.save(request);
                auditFailure(request, "DELIVERY_BOUNCED", result.failureCode(), now);
                yield DeliveryStatus.BOUNCED;
            }
            case TIMEOUT, FAILED -> handleFailure(
                    request,
                    attempt,
                    result.failureCode() == null ? "PROVIDER_FAILED" : result.failureCode(),
                    result.failureDetail(),
                    now
            );
        };
    }

    private DeliveryStatus handleFailure(
            DeliveryRequest request,
            DeliveryAttempt attempt,
            String code,
            String detail,
            Instant now
    ) {
        RetryClassification classification = retryClassifier.classify(code);
        if ("SIGNED_LINK_EXPIRED".equals(code) || "SIGNED_LINK_INVALID".equals(code)) {
            classification = RetryClassification.REJECT;
        }
        request.completeFailed(attempt, code, detail, now);

        if (classification == RetryClassification.TRANSIENT && !request.attemptsExhausted()) {
            Instant next = now.plus(backoff.delayForAttempt(attempt.attemptNumber()));
            request.scheduleRetry(next, now);
            repository.save(request);
            auditFailure(request, "DELIVERY_RETRY_SCHEDULED", code, now);
            return DeliveryStatus.QUEUED;
        }

        repository.save(request);
        return deadLetter(request, code, detail, now);
    }

    private DeliveryStatus deadLetter(DeliveryRequest request, String code, String detail, Instant now) {
        DeliveryDeadLetter dlq = DeliveryDeadLetter.open(
                request.id(),
                request.tenantId(),
                request.noteVersionId(),
                request.recipient().email(),
                code,
                detail,
                request.attempts().size(),
                now
        );
        repository.saveDeadLetter(dlq);
        request.markDeadLetter(dlq.id(), now);
        repository.save(request);
        auditPort.record(
                request.tenantId().value(),
                ACTOR_SYSTEM,
                "DELIVERY_DEAD_LETTERED",
                "DeliveryRequest",
                request.id(),
                Map.of(
                        "deadLetterId", dlq.id().toString(),
                        "failureCode", code,
                        "attempts", request.attempts().size()
                ),
                now
        );
        return DeliveryStatus.FAILED;
    }

    private void auditSuccess(DeliveryRequest request, String action, Instant now) {
        auditPort.record(
                request.tenantId().value(),
                ACTOR_SYSTEM,
                action,
                "DeliveryRequest",
                request.id(),
                Map.of(
                        "status", request.status().name(),
                        "recipientKind", request.recipient().kind().name(),
                        "providerAcceptedNotDelivered",
                        String.valueOf(request.status() == DeliveryStatus.PROVIDER_ACCEPTED)
                ),
                now
        );
    }

    private void auditFailure(DeliveryRequest request, String action, String code, Instant now) {
        auditPort.record(
                request.tenantId().value(),
                ACTOR_SYSTEM,
                action,
                "DeliveryRequest",
                request.id(),
                Map.of(
                        "status", request.status().name(),
                        "failureCode", code == null ? "" : code
                ),
                now
        );
    }

    /**
     * Confirms final delivery after provider webhook / poll — never inferred from acceptance alone.
     * Already-{@link DeliveryStatus#DELIVERED} is idempotent (webhook at-least-once).
     */
    public DeliveryStatus confirmDelivered(UUID tenantId, UUID deliveryRequestId) {
        Instant now = clock.now();
        DeliveryRequest request = repository
                .findById(com.nanobaseai.actenora.sharedkernel.domain.TenantId.of(tenantId), deliveryRequestId)
                .orElseThrow(() -> new DeliveryDomainException(
                        "DELIVERY_NOT_FOUND",
                        "delivery request not found: " + deliveryRequestId));
        if (request.status() == DeliveryStatus.DELIVERED) {
            return DeliveryStatus.DELIVERED;
        }
        if (request.status() != DeliveryStatus.PROVIDER_ACCEPTED) {
            throw new DeliveryDomainException(
                    "INVALID_STATUS",
                    "only PROVIDER_ACCEPTED can be confirmed delivered, was " + request.status());
        }
        DeliveryAttempt attempt = request.latestAttempt()
                .orElseThrow(() -> new DeliveryDomainException("NO_ATTEMPT", "no attempt to confirm"));
        request.completeDelivered(attempt, now);
        repository.save(request);
        auditSuccess(request, "DELIVERY_DELIVERED", now);
        return DeliveryStatus.DELIVERED;
    }

    /**
     * FAZ 31 — resolve delivery request id by provider message id.
     */
    public UUID resolveByProviderMessageId(UUID tenantId, String providerMessageId) {
        if (providerMessageId == null || providerMessageId.isBlank()) {
            throw new DeliveryDomainException(
                    "INVALID_PROVIDER_MESSAGE_ID",
                    "providerMessageId is required"
            );
        }
        return repository
                .findByProviderMessageId(
                        com.nanobaseai.actenora.sharedkernel.domain.TenantId.of(tenantId),
                        providerMessageId.trim()
                )
                .map(DeliveryRequest::id)
                .orElseThrow(() -> new DeliveryDomainException(
                        "DELIVERY_NOT_FOUND",
                        "delivery request not found for providerMessageId: " + providerMessageId
                ));
    }
}
