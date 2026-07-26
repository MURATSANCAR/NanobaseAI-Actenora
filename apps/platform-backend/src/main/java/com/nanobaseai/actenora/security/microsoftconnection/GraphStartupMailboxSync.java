package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.microsoftconnection.application.port.SubscriptionStore;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.config.MicrosoftGraphSpringProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Catches up calendar meetings after restart when Graph webhook work was lost from in-memory outbox.
 */
@Component
@ConditionalOnProperty(name = "actenora.microsoft-graph.enabled", havingValue = "true")
public final class GraphStartupMailboxSync {

    private static final Logger log = LoggerFactory.getLogger(GraphStartupMailboxSync.class);

    private final GraphMailboxSyncService graphMailboxSyncService;
    private final SubscriptionStore subscriptionStore;
    private final MicrosoftGraphSpringProperties graphProperties;

    public GraphStartupMailboxSync(
            GraphMailboxSyncService graphMailboxSyncService,
            SubscriptionStore subscriptionStore,
            MicrosoftGraphSpringProperties graphProperties
    ) {
        this.graphMailboxSyncService = Objects.requireNonNull(graphMailboxSyncService);
        this.subscriptionStore = Objects.requireNonNull(subscriptionStore);
        this.graphProperties = Objects.requireNonNull(graphProperties);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncSubscribedMailboxesOnStartup() {
        Set<MailboxRef> mailboxes = new LinkedHashSet<>();
        for (UUID tenantId : subscriptionStore.distinctTenantIds()) {
            subscriptionStore.findAllForTenant(tenantId).forEach(subscription ->
                    GraphChangeNotificationProcessor.parseMailboxUserId(subscription.resource())
                            .ifPresent(userId -> mailboxes.add(new MailboxRef(tenantId, userId))));
        }
        if (mailboxes.isEmpty() && StringUtils.hasText(graphProperties.getDefaultMailboxUserId())) {
            subscriptionStore.distinctTenantIds().stream().findFirst().ifPresent(tenantId ->
                    mailboxes.add(new MailboxRef(tenantId, graphProperties.getDefaultMailboxUserId())));
        }
        for (MailboxRef mailbox : mailboxes) {
            try {
                GraphMailboxSyncService.SyncResult result =
                        graphMailboxSyncService.syncMailbox(mailbox.tenantId(), mailbox.userId());
                log.info(
                        "Graph startup mailbox sync tenantId={} mailbox={} eventsSynced={}",
                        mailbox.tenantId(),
                        mailbox.userId(),
                        result.eventsSynced());
            } catch (RuntimeException ex) {
                log.warn(
                        "Graph startup mailbox sync failed tenantId={} mailbox={}: {}",
                        mailbox.tenantId(),
                        mailbox.userId(),
                        ex.getMessage());
            }
        }
    }

    private record MailboxRef(UUID tenantId, String userId) {
    }
}
