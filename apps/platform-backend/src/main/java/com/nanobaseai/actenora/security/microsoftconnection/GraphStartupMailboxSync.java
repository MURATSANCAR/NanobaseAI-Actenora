package com.nanobaseai.actenora.security.microsoftconnection;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Catches up calendar meetings after restart when Graph webhook work was lost from in-memory outbox.
 */
@Component
@ConditionalOnProperty(name = "actenora.microsoft-graph.enabled", havingValue = "true")
public final class GraphStartupMailboxSync {

    private final GraphMailboxSyncRunner graphMailboxSyncRunner;
    private final boolean recoverEmptyDelta;
    private final boolean mailboxSyncOnStartup;

    public GraphStartupMailboxSync(
            GraphMailboxSyncRunner graphMailboxSyncRunner,
            @Value("${actenora.microsoft-graph.mailbox-sync-recover-empty-delta:true}") boolean recoverEmptyDelta,
            @Value("${actenora.microsoft-graph.mailbox-sync-on-startup:true}") boolean mailboxSyncOnStartup
    ) {
        this.graphMailboxSyncRunner = Objects.requireNonNull(graphMailboxSyncRunner);
        this.recoverEmptyDelta = recoverEmptyDelta;
        this.mailboxSyncOnStartup = mailboxSyncOnStartup;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncSubscribedMailboxesOnStartup() {
        if (!mailboxSyncOnStartup) {
            return;
        }
        graphMailboxSyncRunner.syncAll("startup", recoverEmptyDelta);
    }
}
