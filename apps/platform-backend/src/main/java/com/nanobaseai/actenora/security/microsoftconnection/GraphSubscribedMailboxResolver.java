package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.microsoftconnection.application.port.SubscriptionStore;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.config.MicrosoftGraphSpringProperties;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Resolves Graph calendar mailboxes from active subscriptions (plus configured default). */
final class GraphSubscribedMailboxResolver {

    private final SubscriptionStore subscriptionStore;
    private final MicrosoftGraphSpringProperties graphProperties;

    GraphSubscribedMailboxResolver(
            SubscriptionStore subscriptionStore,
            MicrosoftGraphSpringProperties graphProperties
    ) {
        this.subscriptionStore = Objects.requireNonNull(subscriptionStore);
        this.graphProperties = Objects.requireNonNull(graphProperties);
    }

    Set<MailboxRef> resolve() {
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
        return mailboxes;
    }

    record MailboxRef(UUID tenantId, String userId) {
    }
}
