package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.microsoftconnection.api.MicrosoftConnectionApi;
import com.nanobaseai.actenora.microsoftconnection.application.model.GraphChangeNotification;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import com.nanobaseai.actenora.sharedkernel.messaging.port.OutboxPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wave 5 — processes Graph change notifications after idempotent claim:
 * enqueues a durable work item and triggers calendar sync when the resource is a mailbox.
 */
@Component
@ConditionalOnProperty(name = "actenora.microsoft-graph.enabled", havingValue = "true")
public final class GraphChangeNotificationProcessor {

    private static final Logger log = LoggerFactory.getLogger(GraphChangeNotificationProcessor.class);

    static final String GRAPH_CHANGE_RECEIVED = "microsoft.GraphChangeNotificationReceived.v1";

    private static final Pattern USER_EVENTS = Pattern.compile("users/([^/]+)/events");

    private final MicrosoftConnectionApi microsoftConnectionApi;
    private final Optional<OutboxPublisher> outboxPublisher;

    public GraphChangeNotificationProcessor(
            MicrosoftConnectionApi microsoftConnectionApi,
            ObjectProvider<OutboxPublisher> outboxPublisher
    ) {
        this.microsoftConnectionApi = Objects.requireNonNull(microsoftConnectionApi, "microsoftConnectionApi");
        this.outboxPublisher = Optional.ofNullable(outboxPublisher.getIfAvailable());
    }

    public void process(GraphChangeNotification notification) {
        Objects.requireNonNull(notification, "notification");
        UUID tenantId = parseTenantId(notification.tenantId()).orElse(null);
        enqueueWorkItem(notification, tenantId);
        if (tenantId == null) {
            log.warn("Graph change notification missing tenantId; subscriptionId={}", notification.subscriptionId());
            return;
        }
        parseMailboxUserId(notification.resource()).ifPresent(userId -> {
            try {
                microsoftConnectionApi.syncCalendar(tenantId, userId);
            } catch (RuntimeException ex) {
                log.warn(
                        "Calendar sync after Graph notification failed tenantId={} userId={} subscriptionId={}: {}",
                        tenantId,
                        userId,
                        notification.subscriptionId(),
                        ex.getMessage()
                );
            }
        });
    }

    private void enqueueWorkItem(GraphChangeNotification notification, UUID tenantId) {
        if (outboxPublisher.isEmpty()) {
            return;
        }
        TenantId envelopeTenant = tenantId == null ? TenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000000"))
                : TenantId.of(tenantId);
        String payload = "{"
                + "\"notificationId\":\"" + escape(notification.notificationId()) + "\","
                + "\"subscriptionId\":\"" + escape(notification.subscriptionId()) + "\","
                + "\"changeType\":\"" + escape(notification.changeType()) + "\","
                + "\"resource\":\"" + escape(notification.resource()) + "\","
                + "\"tenantId\":\"" + (tenantId == null ? "" : tenantId) + "\""
                + "}";
        outboxPublisher.get().enqueue(new EventEnvelope(
                UUID.randomUUID(),
                GRAPH_CHANGE_RECEIVED,
                1,
                Instant.now(),
                envelopeTenant,
                "GraphSubscription",
                notification.subscriptionId(),
                null,
                null,
                null,
                "microsoft-connection",
                payload
        ));
    }

    static Optional<UUID> parseTenantId(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(tenantId.trim()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    static Optional<String> parseMailboxUserId(String resource) {
        if (!StringUtils.hasText(resource)) {
            return Optional.empty();
        }
        Matcher matcher = USER_EVENTS.matcher(resource);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
