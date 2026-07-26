package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.microsoftconnection.application.model.GraphChangeNotification;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;
import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import com.nanobaseai.actenora.sharedkernel.messaging.port.OutboxPublisher;
import com.nanobaseai.actenora.tenant.api.TenantApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
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
 * Validates tenant ownership and atomically enqueues durable Graph change work.
 * Calendar synchronization is deliberately performed by {@link GraphChangeWorkConsumer},
 * outside the Graph callback thread.
 */
@Component
@ConditionalOnProperty(name = "actenora.microsoft-graph.enabled", havingValue = "true")
public final class GraphChangeNotificationProcessor {

    private static final Logger log = LoggerFactory.getLogger(GraphChangeNotificationProcessor.class);

    public static final String GRAPH_CHANGE_RECEIVED = "microsoft.GraphChangeNotificationReceived.v1";

    private static final Pattern USER_EVENTS = Pattern.compile("users/([^/]+)/events", Pattern.CASE_INSENSITIVE);

    private final TenantApi tenantApi;
    private final Optional<OutboxPublisher> outboxPublisher;
    private final Optional<GraphObservability> observability;
    private final boolean inMemoryMessaging;
    private final ObjectProvider<GraphChangeWorkConsumer> graphChangeWorkConsumer;

    public GraphChangeNotificationProcessor(
            TenantApi tenantApi,
            ObjectProvider<OutboxPublisher> outboxPublisher,
            ObjectProvider<GraphObservability> observability,
            ObjectProvider<GraphChangeWorkConsumer> graphChangeWorkConsumer,
            @Value("${actenora.messaging.mode:inmemory}") String messagingMode
    ) {
        this.tenantApi = Objects.requireNonNull(tenantApi, "tenantApi");
        this.outboxPublisher = Optional.ofNullable(outboxPublisher.getIfAvailable());
        this.observability = Optional.ofNullable(observability.getIfAvailable());
        this.graphChangeWorkConsumer = Objects.requireNonNull(graphChangeWorkConsumer, "graphChangeWorkConsumer");
        this.inMemoryMessaging = "inmemory".equalsIgnoreCase(messagingMode);
    }

    public void process(GraphChangeNotification notification) {
        Objects.requireNonNull(notification, "notification");
        Optional<TenantId> tenantId = GraphTenantResolver.resolve(notification.tenantId(), tenantApi);
        if (tenantId.isEmpty()) {
            observability.ifPresent(GraphObservability::recordTenantUnmapped);
            log.error(
                    "GRAPH_TENANT_UNMAPPED subscriptionId={} rawTenantId={}",
                    notification.subscriptionId(),
                    notification.tenantId()
            );
            throw new ActenoraException(
                    "GRAPH_TENANT_UNMAPPED",
                    "No Actenora tenant mapped for Graph tenantId='" + notification.tenantId()
                            + "'; provision TenantApi Entra binding before calendar sync"
            );
        }
        if (inMemoryMessaging) {
            dispatchSynchronously(notification, tenantId.get().value());
            return;
        }
        enqueueWorkItem(notification, tenantId.get().value());
    }

    private void dispatchSynchronously(GraphChangeNotification notification, UUID tenantId) {
        GraphChangeWorkConsumer consumer = graphChangeWorkConsumer.getIfAvailable();
        if (consumer == null) {
            throw new ActenoraException(
                    "GRAPH_SYNC_UNAVAILABLE",
                    "Graph calendar sync consumer is not configured");
        }
        consumer.handle(toEnvelope(notification, tenantId));
    }

    private static EventEnvelope toEnvelope(GraphChangeNotification notification, UUID tenantId) {
        String payload = buildPayload(notification, tenantId);
        UUID eventId = UUID.randomUUID();
        return new EventEnvelope(
                eventId,
                GRAPH_CHANGE_RECEIVED,
                1,
                Instant.now(),
                TenantId.of(tenantId),
                "GraphSubscription",
                notification.subscriptionId(),
                eventId,
                null,
                null,
                "microsoft-connection",
                payload
        );
    }

    private void enqueueWorkItem(GraphChangeNotification notification, UUID tenantId) {
        if (outboxPublisher.isEmpty()) {
            throw new ActenoraException(
                    "GRAPH_DURABLE_MESSAGING_UNAVAILABLE",
                    "Graph notifications require an outbox publisher");
        }
        TenantId envelopeTenant = tenantId == null ? TenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000000"))
                : TenantId.of(tenantId);
        String payload = buildPayload(notification, tenantId);
        UUID eventId = UUID.randomUUID();
        outboxPublisher.get().enqueue(new EventEnvelope(
                eventId,
                GRAPH_CHANGE_RECEIVED,
                1,
                Instant.now(),
                envelopeTenant,
                "GraphSubscription",
                notification.subscriptionId(),
                eventId,
                null,
                null,
                "microsoft-connection",
                payload
        ));
    }

    private static String buildPayload(GraphChangeNotification notification, UUID tenantId) {
        return "{"
                + "\"notificationId\":\"" + escape(notification.notificationId()) + "\","
                + "\"subscriptionId\":\"" + escape(notification.subscriptionId()) + "\","
                + "\"changeType\":\"" + escape(notification.changeType()) + "\","
                + "\"resource\":\"" + escape(notification.resource()) + "\","
                + "\"tenantId\":\"" + (tenantId == null ? "" : tenantId) + "\""
                + "}";
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
