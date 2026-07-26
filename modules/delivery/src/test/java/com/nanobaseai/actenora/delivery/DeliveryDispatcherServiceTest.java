package com.nanobaseai.actenora.delivery;

import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.delivery.application.DeliveryDispatcherService;
import com.nanobaseai.actenora.delivery.application.EnqueueDeliveryCommand;
import com.nanobaseai.actenora.delivery.application.EnqueueDeliveryResult;
import com.nanobaseai.actenora.delivery.application.port.DeliveryMailProvider;
import com.nanobaseai.actenora.delivery.application.worker.DeliveryWorker;
import com.nanobaseai.actenora.delivery.domain.DeliveryDomainException;
import com.nanobaseai.actenora.delivery.domain.DeliveryPolicySnapshot;
import com.nanobaseai.actenora.delivery.domain.DeliveryRecipient;
import com.nanobaseai.actenora.delivery.domain.DeliveryRequest;
import com.nanobaseai.actenora.delivery.domain.DeliveryStatus;
import com.nanobaseai.actenora.delivery.infrastructure.approval.InMemoryNoteApprovalGate;
import com.nanobaseai.actenora.delivery.infrastructure.audit.RecordingDeliveryAuditPort;
import com.nanobaseai.actenora.delivery.infrastructure.mail.MailHogMailProvider;
import com.nanobaseai.actenora.delivery.infrastructure.mail.MicrosoftGraphMailProvider;
import com.nanobaseai.actenora.delivery.infrastructure.pdf.InMemoryPdfAttachmentPort;
import com.nanobaseai.actenora.delivery.infrastructure.persistence.InMemoryDeliveryRequestRepository;
import com.nanobaseai.actenora.delivery.infrastructure.portal.HmacSignedPortalLinkService;
import com.nanobaseai.actenora.delivery.infrastructure.ratelimit.FixedWindowDeliveryRateLimiter;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.ExponentialBackoff;
import com.nanobaseai.actenora.sharedkernel.messaging.RetryClassifier;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliveryDispatcherServiceTest {

    private static final Instant START = Instant.parse("2026-07-25T12:00:00Z");

    private MutableClock mutableClock;
    private InstantClock clock;
    private InMemoryDeliveryRequestRepository repository;
    private InMemoryNoteApprovalGate approvalGate;
    private MailHogMailProvider mailProvider;
    private InMemoryPdfAttachmentPort pdfPort;
    private HmacSignedPortalLinkService portalLinks;
    private RecordingDeliveryAuditPort audit;
    private DeliveryDispatcherService dispatcher;
    private DeliveryWorker worker;
    private TenantId tenant;
    private UUID noteVersionId;
    private ApprovalId approvalId;

    @BeforeEach
    void setUp() {
        mutableClock = new MutableClock(START);
        clock = new InstantClock(mutableClock);
        repository = new InMemoryDeliveryRequestRepository();
        approvalGate = new InMemoryNoteApprovalGate();
        mailProvider = MailHogMailProvider.localDefaults(clock::now);
        pdfPort = new InMemoryPdfAttachmentPort();
        portalLinks = new HmacSignedPortalLinkService(
                "test-portal-secret-key",
                "https://portal.nanobase.ai"
        );
        audit = new RecordingDeliveryAuditPort();
        dispatcher = createDispatcher(new FixedWindowDeliveryRateLimiter(60, clock));
        worker = new DeliveryWorker(repository, dispatcher, clock, 16);
        tenant = TenantId.random();
        noteVersionId = UUID.randomUUID();
        approvalId = ApprovalId.of(UUID.randomUUID());
        approvalGate.grant(tenant, approvalId, noteVersionId);
    }

    private DeliveryDispatcherService createDispatcher(FixedWindowDeliveryRateLimiter limiter) {
        return new DeliveryDispatcherService(
                repository,
                approvalGate,
                mailProvider,
                limiter,
                pdfPort,
                portalLinks,
                audit,
                clock,
                new ExponentialBackoff(Duration.ofSeconds(1), Duration.ofMinutes(5), 0.0),
                new RetryClassifier.Default(),
                Duration.ofSeconds(5)
        );
    }

    @Test
    void duplicateSendForSameNoteVersionAndRecipientIsSuppressed() {
        EnqueueDeliveryResult first = enqueue(List.of(DeliveryRecipient.external("a@ex.com", "A")));
        EnqueueDeliveryResult second = enqueue(List.of(DeliveryRecipient.external("a@ex.com", "A")));

        assertEquals(1, first.createdIds().size());
        assertEquals(0, first.duplicateIds().size());
        assertEquals(0, second.createdIds().size());
        assertEquals(1, second.duplicateIds().size());
        assertEquals(first.createdIds().getFirst(), second.duplicateIds().getFirst());
        assertEquals(1, audit.ofAction("DELIVERY_DUPLICATE_SUPPRESSED").size());
    }

    @Test
    void externalRecipientsReceiveIsolatedMails() {
        enqueue(List.of(
                DeliveryRecipient.external("ext1@client.com", "E1"),
                DeliveryRecipient.external("ext2@client.com", "E2"),
                DeliveryRecipient.internal("me@tenant.com", "Me")
        ));

        worker.pollOnce();

        assertEquals(3, mailProvider.sentMails().size());
        assertEquals(3, mailProvider.sentMails().stream().map(MailHogMailProvider.CapturedMail::to).distinct().count());
    }

    @Test
    void unapprovedNoteCannotBeEnqueued() {
        ApprovalId other = ApprovalId.of(UUID.randomUUID());
        DeliveryDomainException ex = assertThrows(
                DeliveryDomainException.class,
                () -> dispatcher.enqueue(new EnqueueDeliveryCommand(
                        tenant,
                        noteVersionId,
                        other,
                        List.of(DeliveryRecipient.external("x@y.com", "X")),
                        DeliveryPolicySnapshot.defaults(),
                        "Subject",
                        "Body",
                        "actor-1"
                )));
        assertEquals("NOTE_NOT_APPROVED", ex.code());
        assertTrue(mailProvider.sentMails().isEmpty());
    }

    @Test
    void rateLimitDefersSendWithoutConsumingAttemptBudget() {
        dispatcher = createDispatcher(new FixedWindowDeliveryRateLimiter(1, clock));
        worker = new DeliveryWorker(repository, dispatcher, clock, 16);

        enqueue(List.of(DeliveryRecipient.external("one@ex.com", "1")));
        enqueue(List.of(DeliveryRecipient.external("two@ex.com", "2")));

        List<DeliveryStatus> outcomes = worker.pollOnce();
        assertEquals(2, outcomes.size());
        assertTrue(outcomes.contains(DeliveryStatus.PROVIDER_ACCEPTED));
        assertTrue(outcomes.contains(DeliveryStatus.DEFERRED));
        assertEquals(1, mailProvider.sentMails().size());
        assertFalse(audit.ofAction("DELIVERY_RATE_LIMITED").isEmpty());

        DeliveryRequest deferred = repository
                .findByNoteVersionAndRecipient(
                        tenant,
                        noteVersionId,
                        mailProvider.sentMails().getFirst().to().equals("one@ex.com") ? "two@ex.com" : "one@ex.com")
                .orElseThrow();

        assertTrue(deferred.attempts().isEmpty());
        assertEquals(DeliveryStatus.QUEUED, deferred.status());
        assertTrue(deferred.nextAttemptAt().isPresent());
    }

    @Test
    void providerTimeoutSchedulesRetry() {
        mailProvider.forceOutcome(DeliveryMailProvider.SendOutcome.TIMEOUT, "PROVIDER_TIMEOUT");
        EnqueueDeliveryResult result = enqueue(List.of(DeliveryRecipient.external("t@ex.com", "T")));
        DeliveryStatus status = worker.pollOnce().getFirst();
        assertEquals(DeliveryStatus.QUEUED, status);

        DeliveryRequest request = repository.findById(tenant, result.createdIds().getFirst().value()).orElseThrow();
        assertEquals(1, request.attempts().size());
        assertTrue(request.nextAttemptAt().isPresent());
        assertEquals("PROVIDER_TIMEOUT", request.attempts().getFirst().failureCode().orElseThrow());
        assertFalse(audit.ofAction("DELIVERY_RETRY_SCHEDULED").isEmpty());
    }

    @Test
    void retryEventuallySucceedsAfterTransientFailure() {
        mailProvider.forceOutcome(DeliveryMailProvider.SendOutcome.TIMEOUT, "PROVIDER_TIMEOUT");
        EnqueueDeliveryResult result = enqueue(List.of(DeliveryRecipient.external("r@ex.com", "R")));
        worker.pollOnce();

        mailProvider.clearForcedOutcome();
        mutableClock.set(START.plusSeconds(2));
        DeliveryStatus status = worker.pollOnce().getFirst();
        assertEquals(DeliveryStatus.PROVIDER_ACCEPTED, status);

        DeliveryRequest request = repository.findById(tenant, result.createdIds().getFirst().value()).orElseThrow();
        assertEquals(2, request.attempts().size());
        assertEquals(DeliveryStatus.PROVIDER_ACCEPTED, request.status());
        assertNotEquals(DeliveryStatus.DELIVERED, request.status());
    }

    @Test
    void exhaustedRetriesGoToDlq() {
        DeliveryPolicySnapshot policy = DeliveryPolicySnapshot.defaults().withMaxAttempts(2);
        mailProvider.forceOutcome(DeliveryMailProvider.SendOutcome.FAILED, "POISON");
        EnqueueDeliveryResult result = enqueue(List.of(DeliveryRecipient.external("dlq@ex.com", "D")), policy);

        DeliveryStatus first = worker.pollOnce().getFirst();
        assertEquals(DeliveryStatus.FAILED, first);
        assertEquals(1, repository.listOpenDeadLetters(10).size());
        assertEquals(result.createdIds().getFirst().value(), repository.listOpenDeadLetters(10).getFirst().deliveryRequestId());
        assertFalse(audit.ofAction("DELIVERY_DEAD_LETTERED").isEmpty());
    }

    @Test
    void signedPortalLinkExpiresAndBlocksSend() {
        DeliveryPolicySnapshot sensitive = new DeliveryPolicySnapshot(
                true, true, false, true, true, 3, 60, Duration.ofMinutes(5),
                DeliveryPolicySnapshot.PROVIDER_MAILHOG
        );
        EnqueueDeliveryResult result = enqueue(
                List.of(DeliveryRecipient.external("sec@ex.com", "S")),
                sensitive
        );
        DeliveryRequest queued = repository.findById(tenant, result.createdIds().getFirst().value()).orElseThrow();
        assertTrue(queued.signedPortalLink().isPresent());
        assertFalse(queued.pdfAttachment().orElseThrow().attach());

        mutableClock.set(START.plus(Duration.ofMinutes(10)));
        DeliveryStatus status = worker.pollOnce().getFirst();
        assertEquals(DeliveryStatus.FAILED, status);
        assertTrue(mailProvider.sentMails().isEmpty());
        assertEquals("SIGNED_LINK_EXPIRED", repository.listOpenDeadLetters(10).getFirst().failureCode());
    }

    @Test
    void providerAcceptedIsNotDeliveredUntilConfirmed() {
        EnqueueDeliveryResult result = enqueue(List.of(DeliveryRecipient.internal("i@t.com", "I")));
        assertEquals(DeliveryStatus.PROVIDER_ACCEPTED, worker.pollOnce().getFirst());

        DeliveryRequest request = repository.findById(tenant, result.createdIds().getFirst().value()).orElseThrow();
        assertEquals(DeliveryStatus.PROVIDER_ACCEPTED, request.status());
        assertTrue(request.latestAttempt().orElseThrow().providerAcceptedButNotDelivered());

        DeliveryStatus delivered = dispatcher.confirmDelivered(tenant.value(), request.id());
        assertEquals(DeliveryStatus.DELIVERED, delivered);
        assertEquals(DeliveryStatus.DELIVERED, dispatcher.confirmDelivered(tenant.value(), request.id()));
    }

    @Test
    void confirmViaProviderMessageIdResolvesRequest() {
        EnqueueDeliveryResult result = enqueue(List.of(DeliveryRecipient.internal("p@t.com", "P")));
        assertEquals(DeliveryStatus.PROVIDER_ACCEPTED, worker.pollOnce().getFirst());

        DeliveryRequest request = repository.findById(tenant, result.createdIds().getFirst().value()).orElseThrow();
        String providerMessageId = request.latestAttempt()
                .flatMap(a -> a.providerMessage())
                .map(m -> m.providerMessageId())
                .orElseThrow();

        UUID resolved = dispatcher.resolveByProviderMessageId(tenant.value(), providerMessageId);
        assertEquals(request.id(), resolved);
        assertEquals(DeliveryStatus.DELIVERED, dispatcher.confirmDelivered(tenant.value(), resolved));
    }

    @Test
    void microsoftGraphPortValidatesConfigurationAndExposesSendContract() {
        MicrosoftGraphMailProvider graph = new MicrosoftGraphMailProvider(
                new MicrosoftGraphMailProvider.GraphMailConfig("tid", "cid", "sender@contoso.com", true),
                clock::now
        );
        graph.validateConfiguration();
        assertTrue(graph.getProviderStatus().healthy());
        assertEquals(DeliveryPolicySnapshot.PROVIDER_MICROSOFT_GRAPH, graph.providerType());

        DeliveryRequest request = DeliveryRequest.enqueue(
                tenant,
                noteVersionId,
                approvalId,
                DeliveryRecipient.external("a@b.com", "A"),
                DeliveryPolicySnapshot.defaults().withProvider(DeliveryPolicySnapshot.PROVIDER_MICROSOFT_GRAPH),
                "s",
                "b",
                clock.now()
        );
        var outcome = graph.send(new DeliveryMailProvider.SendCommand(
                request,
                Optional.empty(),
                Optional.empty(),
                Duration.ofSeconds(5)
        ));
        assertEquals(DeliveryMailProvider.SendOutcome.FAILED, outcome.outcome());
        assertEquals("GRAPH_SEND_NOT_WIRED", outcome.failureCode());
    }

    @Test
    void deliveryWorkerIsSeparatelyRunnable() {
        assertEquals("delivery-worker", DeliveryWorker.SERVICE_NAME);
        enqueue(List.of(DeliveryRecipient.external("w@ex.com", "W")));
        assertFalse(worker.pollOnce().isEmpty());
        worker.beginDrain();
        assertTrue(worker.isDraining());
        assertTrue(worker.pollOnce().isEmpty());
    }

    private EnqueueDeliveryResult enqueue(List<DeliveryRecipient> recipients) {
        return enqueue(recipients, DeliveryPolicySnapshot.defaults());
    }

    private EnqueueDeliveryResult enqueue(List<DeliveryRecipient> recipients, DeliveryPolicySnapshot policy) {
        return dispatcher.enqueue(new EnqueueDeliveryCommand(
                tenant,
                noteVersionId,
                approvalId,
                recipients,
                policy,
                "Meeting notes",
                "Please review",
                "actor-1"
        ));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
