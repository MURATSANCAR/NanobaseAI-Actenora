package com.nanobaseai.actenora.security.microsoftconnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/** Runs calendar sync for every subscribed mailbox (startup + periodic safety net). */
final class GraphMailboxSyncRunner {

    private static final Logger log = LoggerFactory.getLogger(GraphMailboxSyncRunner.class);

    private final GraphSubscribedMailboxResolver mailboxResolver;
    private final GraphMailboxSyncService graphMailboxSyncService;

    GraphMailboxSyncRunner(
            GraphSubscribedMailboxResolver mailboxResolver,
            GraphMailboxSyncService graphMailboxSyncService
    ) {
        this.mailboxResolver = Objects.requireNonNull(mailboxResolver);
        this.graphMailboxSyncService = Objects.requireNonNull(graphMailboxSyncService);
    }

    void syncAll(String trigger, boolean recoverEmptyDelta) {
        for (GraphSubscribedMailboxResolver.MailboxRef mailbox : mailboxResolver.resolve()) {
            try {
                GraphMailboxSyncService.SyncResult result = graphMailboxSyncService.syncMailbox(
                        mailbox.tenantId(),
                        mailbox.userId(),
                        recoverEmptyDelta
                );
                log.info(
                        "Graph mailbox sync trigger={} tenantId={} mailbox={} eventsSynced={} recovered={}",
                        trigger,
                        mailbox.tenantId(),
                        mailbox.userId(),
                        result.eventsSynced(),
                        result.recoveredFromEmptyDelta());
            } catch (RuntimeException ex) {
                log.warn(
                        "Graph mailbox sync failed trigger={} tenantId={} mailbox={}: {}",
                        trigger,
                        mailbox.tenantId(),
                        mailbox.userId(),
                        ex.getMessage());
            }
        }
    }
}
