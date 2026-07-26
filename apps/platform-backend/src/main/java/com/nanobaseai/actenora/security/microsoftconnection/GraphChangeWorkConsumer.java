package com.nanobaseai.actenora.security.microsoftconnection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Objects;

/**
 * Executes durable Graph calendar work after the webhook transaction commits.
 */
@Component
@ConditionalOnProperty(name = "actenora.microsoft-graph.enabled", havingValue = "true")
public final class GraphChangeWorkConsumer {

    private final GraphMailboxSyncService graphMailboxSyncService;
    private final ObjectMapper objectMapper;

    public GraphChangeWorkConsumer(
            GraphMailboxSyncService graphMailboxSyncService,
            ObjectMapper objectMapper
    ) {
        this.graphMailboxSyncService = Objects.requireNonNull(graphMailboxSyncService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public void handle(EventEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        JsonNode payload;
        try {
            payload = objectMapper.readTree(envelope.payloadJson());
        } catch (IOException ex) {
            throw new IllegalArgumentException("Graph work item payload is invalid", ex);
        }
        String resource = text(payload, "resource");
        String userId = GraphChangeNotificationProcessor.parseMailboxUserId(resource)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Graph notification resource does not identify a mailbox: " + resource));
        graphMailboxSyncService.syncMailbox(envelope.tenantId().value(), userId);
    }

    private static String text(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
