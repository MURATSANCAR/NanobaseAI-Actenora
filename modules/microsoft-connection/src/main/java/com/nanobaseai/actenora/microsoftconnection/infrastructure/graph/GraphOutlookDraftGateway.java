package com.nanobaseai.actenora.microsoftconnection.infrastructure.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobaseai.actenora.microsoftconnection.application.model.OutlookDraftRequest;
import com.nanobaseai.actenora.microsoftconnection.application.model.OutlookDraftResult;
import com.nanobaseai.actenora.microsoftconnection.application.port.OutlookDraftGateway;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Creates an Outlook draft through Microsoft Graph. It never sends the message.
 */
public final class GraphOutlookDraftGateway implements OutlookDraftGateway {

    private final GraphHttpClient http;
    private final ObjectMapper objectMapper;

    public GraphOutlookDraftGateway(GraphHttpClient http, ObjectMapper objectMapper) {
        this.http = Objects.requireNonNull(http, "http");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public OutlookDraftResult create(UUID tenantId, OutlookDraftRequest request) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(request, "request");

        ObjectNode message = objectMapper.createObjectNode();
        message.put("subject", request.subject());
        ObjectNode body = message.putObject("body");
        body.put("contentType", "HTML");
        body.put("content", request.bodyHtml());
        ArrayNode recipients = message.putArray("toRecipients");
        request.toRecipients().forEach(address -> recipients.addObject()
                .putObject("emailAddress")
                .put("address", address));
        message.putArray("internetMessageHeaders")
                .addObject()
                .put("name", "x-actenora-idempotency-key")
                .put("value", request.idempotencyKey());

        String path = "v1.0/users/" + encodePathSegment(request.mailboxUserId()) + "/messages";
        String responseBody = http.sendAtMostOnce(token ->
                http.authorizedJson(path, "POST", message.toString(), token)).body();
        try {
            JsonNode response = objectMapper.readTree(responseBody);
            String id = requiredText(response, "id");
            String webLink = optionalText(response, "webLink");
            return new OutlookDraftResult(id, webLink, false);
        } catch (GraphApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw GraphApiException.transport("Graph draft response could not be parsed", ex);
        }
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String requiredText(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null || value.isBlank()) {
            throw GraphApiException.transport(
                    "Graph draft response is missing " + field,
                    new IllegalStateException(field));
        }
        return value;
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
