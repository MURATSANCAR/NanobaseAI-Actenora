package com.nanobaseai.actenora.microsoftconnection.infrastructure.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobaseai.actenora.microsoftconnection.application.model.DirectoryUser;
import com.nanobaseai.actenora.microsoftconnection.application.port.DirectoryGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Graph {@code /users/{id}} adapter. Directory read is optional — 403/configuration
 * errors degrade to empty so attendance sync can continue without User.Read.All.
 */
public final class GraphDirectoryGateway implements DirectoryGateway {

    private static final Logger log = LoggerFactory.getLogger(GraphDirectoryGateway.class);
    private static final Pattern GUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final GraphHttpClient http;
    private final ObjectMapper objectMapper;

    public GraphDirectoryGateway(GraphHttpClient http, ObjectMapper objectMapper) {
        this.http = Objects.requireNonNull(http, "http");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public Optional<DirectoryUser> resolveUser(UUID tenantId, String objectId) {
        if (objectId == null || objectId.isBlank() || !GUID.matcher(objectId.trim()).matches()) {
            return Optional.empty();
        }
        String id = objectId.trim();
        String path = "v1.0/users/" + URLEncoder.encode(id, StandardCharsets.UTF_8)
                + "?$select=id,mail,userPrincipalName,displayName";
        try {
            var response = http.send(token -> http.authorizedGet(path, token));
            JsonNode node = objectMapper.readTree(response.body());
            String resolvedId = text(node, "id");
            if (resolvedId == null) {
                return Optional.empty();
            }
            return Optional.of(new DirectoryUser(
                    resolvedId,
                    text(node, "displayName"),
                    text(node, "mail"),
                    text(node, "userPrincipalName")
            ));
        } catch (GraphApiException ex) {
            if (GraphApiException.CODE_NOT_FOUND.equals(ex.code())
                    || GraphApiException.CODE_CONFIGURATION.equals(ex.code())
                    || GraphApiException.CODE_UNAUTHORIZED.equals(ex.code())) {
                log.debug("Directory user resolve skipped objectId={} code={}: {}",
                        id, ex.code(), ex.getMessage());
                return Optional.empty();
            }
            throw ex;
        } catch (Exception ex) {
            throw GraphApiException.transport("Failed to resolve directory user", ex);
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }
}
