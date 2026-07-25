package com.nanobaseai.actenora.microsoftconnection.infrastructure.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobaseai.actenora.microsoftconnection.application.model.MailSendRequest;
import com.nanobaseai.actenora.microsoftconnection.application.model.MailSendResult;
import com.nanobaseai.actenora.microsoftconnection.application.port.MailGateway;

import java.util.Objects;
import java.util.UUID;

/**
 * Graph sendMail adapter.
 */
public final class GraphMailGateway implements MailGateway {

    private final GraphHttpClient http;
    private final ObjectMapper objectMapper;

    public GraphMailGateway(GraphHttpClient http, ObjectMapper objectMapper) {
        this.http = Objects.requireNonNull(http, "http");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public MailSendResult send(UUID tenantId, MailSendRequest request) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(request, "request");
        ObjectNode message = objectMapper.createObjectNode();
        message.put("subject", request.subject());
        ObjectNode body = message.putObject("body");
        body.put("contentType", "HTML");
        body.put("content", request.bodyHtml());
        ArrayNode to = message.putArray("toRecipients");
        for (String recipient : request.toRecipients()) {
            ObjectNode entry = to.addObject();
            ObjectNode email = entry.putObject("emailAddress");
            email.put("address", recipient);
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("message", message);
        payload.put("saveToSentItems", true);
        String path = "v1.0/users/" + request.mailboxUserId() + "/sendMail";
        http.send(token -> http.authorizedJson(path, "POST", payload.toString(), token));
        String providerId = request.idempotencyKey() != null
                ? request.idempotencyKey()
                : UUID.randomUUID().toString();
        return MailSendResult.accepted(providerId);
    }
}
