package com.nanobaseai.actenora.microsoftconnection.infrastructure.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobaseai.actenora.microsoftconnection.application.model.GraphSubscription;
import com.nanobaseai.actenora.microsoftconnection.application.model.SubscriptionCreateRequest;
import com.nanobaseai.actenora.microsoftconnection.application.port.SubscriptionGateway;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Graph subscriptions create / renew adapter.
 */
public final class GraphSubscriptionGateway implements SubscriptionGateway {

    private final GraphHttpClient http;
    private final ObjectMapper objectMapper;
    private final InstantClock clock;

    public GraphSubscriptionGateway(GraphHttpClient http, ObjectMapper objectMapper, InstantClock clock) {
        this.http = Objects.requireNonNull(http, "http");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public GraphSubscription create(UUID tenantId, SubscriptionCreateRequest request) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(request, "request");
        Instant expiration = clock.now().plus(request.expirationWindow());
        ObjectNode body = objectMapper.createObjectNode();
        body.put("changeType", request.changeType());
        body.put("notificationUrl", request.notificationUrl());
        if (request.lifecycleNotificationUrl() != null && !request.lifecycleNotificationUrl().isBlank()) {
            body.put("lifecycleNotificationUrl", request.lifecycleNotificationUrl());
        }
        body.put("resource", request.resource());
        body.put("expirationDateTime", expiration.toString());
        if (request.clientState() != null) {
            body.put("clientState", request.clientState());
        }
        var response = http.send(token -> http.authorizedJson("v1.0/subscriptions", "POST", body.toString(), token));
        return parse(tenantId, response.body());
    }

    @Override
    public GraphSubscription renew(UUID tenantId, String subscriptionId, Instant newExpiration) {
        Objects.requireNonNull(subscriptionId, "subscriptionId");
        Objects.requireNonNull(newExpiration, "newExpiration");
        ObjectNode body = objectMapper.createObjectNode();
        body.put("expirationDateTime", newExpiration.toString());
        var response = http.send(token -> http.authorizedJson(
                "v1.0/subscriptions/" + subscriptionId,
                "PATCH",
                body.toString(),
                token
        ));
        return parse(tenantId, response.body());
    }

    @Override
    public Optional<GraphSubscription> get(UUID tenantId, String subscriptionId) {
        Objects.requireNonNull(subscriptionId, "subscriptionId");
        try {
            var response = http.send(token -> http.authorizedGet("v1.0/subscriptions/" + subscriptionId, token));
            return Optional.of(parse(tenantId, response.body()));
        } catch (GraphApiException ex) {
            if (GraphApiException.CODE_NOT_FOUND.equals(ex.code())) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    @Override
    public void delete(UUID tenantId, String subscriptionId) {
        Objects.requireNonNull(subscriptionId, "subscriptionId");
        http.send(token -> http.authorizedJson(
                "v1.0/subscriptions/" + subscriptionId,
                "DELETE",
                null,
                token
        ));
    }

    private GraphSubscription parse(UUID tenantId, String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String id = text(root, "id");
            if (id == null) {
                throw GraphApiException.transport("subscription response missing id", null);
            }
            return new GraphSubscription(
                    tenantId,
                    id,
                    text(root, "resource"),
                    text(root, "changeType"),
                    text(root, "notificationUrl"),
                    text(root, "clientState"),
                    Instant.parse(Objects.requireNonNull(text(root, "expirationDateTime"))),
                    text(root, "applicationId")
            );
        } catch (GraphApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw GraphApiException.transport("Failed to parse subscription", ex);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }
}
