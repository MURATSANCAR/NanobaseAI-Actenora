package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.identity.api.RequiresPermission;
import com.nanobaseai.actenora.identity.api.Permission;
import com.nanobaseai.actenora.microsoftconnection.api.MicrosoftConnectionApi;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.config.MicrosoftGraphSpringProperties;
import com.nanobaseai.actenora.microsoftconnection.application.model.GraphSubscription;
import com.nanobaseai.actenora.microsoftconnection.application.model.SubscriptionCreateRequest;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Operator API for Graph subscriptions (no demo seed — real Graph resources only).
 */
@RestController
@RequestMapping("/api/v1/microsoft/subscriptions")
@ConditionalOnProperty(name = "actenora.microsoft-graph.enabled", havingValue = "true")
public class MicrosoftGraphSubscriptionController {

    private final MicrosoftConnectionApi microsoftConnectionApi;
    private final TranscriptPollWorkStore transcriptPollWorkStore;
    private final GraphMailboxSyncService graphMailboxSyncService;
    private final MicrosoftGraphSpringProperties graphProperties;
    private final boolean recoverEmptyDelta;

    public MicrosoftGraphSubscriptionController(
            MicrosoftConnectionApi microsoftConnectionApi,
            TranscriptPollWorkStore transcriptPollWorkStore,
            GraphMailboxSyncService graphMailboxSyncService,
            MicrosoftGraphSpringProperties graphProperties,
            @Value("${actenora.microsoft-graph.mailbox-sync-recover-empty-delta:true}") boolean recoverEmptyDelta
    ) {
        this.microsoftConnectionApi = Objects.requireNonNull(microsoftConnectionApi);
        this.transcriptPollWorkStore = Objects.requireNonNull(transcriptPollWorkStore);
        this.graphMailboxSyncService = Objects.requireNonNull(graphMailboxSyncService);
        this.graphProperties = Objects.requireNonNull(graphProperties);
        this.recoverEmptyDelta = recoverEmptyDelta;
    }

    @GetMapping
    @RequiresPermission(Permission.TENANT_ADMINISTER)
    public List<SubscriptionView> list() {
        UUID tenantId = TenantSecurityContext.require().tenantId().value();
        return microsoftConnectionApi.listSubscriptions(tenantId).stream()
                .map(SubscriptionView::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.TENANT_ADMINISTER)
    public SubscriptionView create(@RequestBody CreateSubscriptionBody body) {
        UUID tenantId = TenantSecurityContext.require().tenantId().value();
        GraphSubscription created = microsoftConnectionApi.createSubscription(
                tenantId,
                new SubscriptionCreateRequest(
                        body.resource(),
                        body.changeType(),
                        body.notificationUrl(),
                        body.lifecycleNotificationUrl(),
                        body.clientState(),
                        body.expirationWindow() == null ? Duration.ofHours(48) : body.expirationWindow()
                )
        );
        return SubscriptionView.from(created);
    }

    @PostMapping("/sync-mailbox")
    @RequiresPermission(Permission.TENANT_ADMINISTER)
    public MailboxSyncView syncMailbox() {
        UUID tenantId = TenantSecurityContext.require().tenantId().value();
        String mailboxUserId = graphProperties.getDefaultMailboxUserId();
        if (mailboxUserId == null || mailboxUserId.isBlank()) {
            throw new ActenoraException(
                    "GRAPH_MAILBOX_NOT_CONFIGURED",
                    "actenora.microsoft-graph.default-mailbox-user-id is required");
        }
        GraphMailboxSyncService.SyncResult result =
                graphMailboxSyncService.syncMailbox(tenantId, mailboxUserId, recoverEmptyDelta);
        return new MailboxSyncView(result.mailboxUserId(), result.eventsSynced(), result.recoveredFromEmptyDelta());
    }

    @PostMapping("/renew-expiring")
    @RequiresPermission(Permission.OPERATIONS_MANAGE)
    public List<SubscriptionView> renewExpiring() {
        return microsoftConnectionApi.renewExpiringSubscriptions().stream()
                .map(SubscriptionView::from)
                .toList();
    }

    @PostMapping("/transcript-polls/{meetingOccurrenceId}/requeue")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RequiresPermission(Permission.OPERATIONS_MANAGE)
    public void requeueTranscriptPoll(@PathVariable UUID meetingOccurrenceId) {
        UUID tenantId = TenantSecurityContext.require().tenantId().value();
        if (!transcriptPollWorkStore.requeueDeadLetter(tenantId, meetingOccurrenceId, Instant.now())) {
            throw new ActenoraException(
                    "TRANSCRIPT_POLL_NOT_REQUEUEABLE",
                    "No dead-lettered transcript poll exists for the meeting occurrence");
        }
    }

    public record MailboxSyncView(String mailboxUserId, int eventsSynced, boolean recoveredFromEmptyDelta) {
    }

    public record CreateSubscriptionBody(
            String resource,
            String changeType,
            String notificationUrl,
            String lifecycleNotificationUrl,
            String clientState,
            Duration expirationWindow
    ) {
    }

    public record SubscriptionView(
            UUID tenantId,
            String subscriptionId,
            String resource,
            String changeType,
            String notificationUrl,
            String expirationDateTime,
            String applicationId
    ) {
        static SubscriptionView from(GraphSubscription s) {
            return new SubscriptionView(
                    s.tenantId(),
                    s.subscriptionId(),
                    s.resource(),
                    s.changeType(),
                    s.notificationUrl(),
                    s.expirationDateTime().toString(),
                    s.applicationId()
            );
        }
    }
}
