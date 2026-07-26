package com.nanobaseai.actenora.security.aiprocessing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobaseai.actenora.modelmanagement.application.ConfigureCapabilityCommand;
import com.nanobaseai.actenora.modelmanagement.application.ModelDefinitionRepository;
import com.nanobaseai.actenora.modelmanagement.application.ModelDeploymentRepository;
import com.nanobaseai.actenora.modelmanagement.application.ModelRegistryService;
import com.nanobaseai.actenora.modelmanagement.application.RegisterDeploymentCommand;
import com.nanobaseai.actenora.modelmanagement.application.RegisterModelCommand;
import com.nanobaseai.actenora.modelmanagement.domain.ModelCapabilityType;
import com.nanobaseai.actenora.modelmanagement.domain.ModelRegistryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Ensures the model-control registry contains a local deployment for the active NanobaseAI endpoint.
 */
public final class NanobaseAiModelRegistrySync {

    private static final Logger log = LoggerFactory.getLogger(NanobaseAiModelRegistrySync.class);
    private static final UUID SYSTEM_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final ModelRegistryService registry;
    private final ModelDefinitionRepository modelDefinitions;
    private final ModelDeploymentRepository deployments;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public NanobaseAiModelRegistrySync(
            ModelRegistryService registry,
            ModelDefinitionRepository modelDefinitions,
            ModelDeploymentRepository deployments,
            ObjectMapper objectMapper
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.modelDefinitions = Objects.requireNonNull(modelDefinitions, "modelDefinitions");
        this.deployments = Objects.requireNonNull(deployments, "deployments");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public void ensureRegistered(URI baseUrl, Set<String> servedModelIds, boolean enabled) {
        if (!enabled || baseUrl == null) {
            return;
        }
        String servedModelId = resolveServedModelId(baseUrl, servedModelIds);
        String modelKey = toModelKey(servedModelId);
        String deploymentKey = modelKey + "-primary";
        var actor = com.nanobaseai.actenora.modelmanagement.application.ActorPrincipal.operationsAdmin(SYSTEM_ACTOR_ID);
        if (modelDefinitions.findByKey(modelKey).isEmpty()) {
            registry.registerModel(actor, new RegisterModelCommand(
                    modelKey,
                    displayName(servedModelId),
                    "nanobaseai",
                    servedModelId,
                    "qwen",
                    "35B",
                    "local",
                    8192,
                    4096,
                    List.of("en", "tr"),
                    10,
                    0.92,
                    0.65
            ));
            configureDefaultCapabilities(actor, modelKey);
            registry.enableModel(actor, modelKey);
            log.info("NanobaseAI model registered modelKey={} servedModelId={}", modelKey, servedModelId);
        }
        if (deployments.findByKey(deploymentKey).isEmpty()) {
            registry.registerDeployment(actor, new RegisterDeploymentCommand(
                    modelKey,
                    deploymentKey,
                    baseUrl.toString(),
                    hostLabel(baseUrl),
                    "local",
                    "cpu",
                    null,
                    0,
                    8,
                    32,
                    2
            ));
            log.info("NanobaseAI deployment registered deploymentKey={} endpoint={}", deploymentKey, baseUrl);
        }
        try {
            registry.heartbeat(actor, deploymentKey);
        } catch (ModelRegistryException ex) {
            log.warn("NanobaseAI deployment heartbeat failed deploymentKey={} reason={}", deploymentKey, ex.getMessage());
        }
    }

    private void configureDefaultCapabilities(
            com.nanobaseai.actenora.modelmanagement.application.ActorPrincipal actor,
            String modelKey
    ) {
        for (ModelCapabilityType capability : List.of(
                ModelCapabilityType.TRANSCRIPT_EXTRACTION,
                ModelCapabilityType.FINAL_NOTE,
                ModelCapabilityType.SUMMARIZATION,
                ModelCapabilityType.DECISION_EXTRACTION,
                ModelCapabilityType.ACTION_EXTRACTION,
                ModelCapabilityType.RISK_EXTRACTION
        )) {
            double quality = capability == ModelCapabilityType.TRANSCRIPT_EXTRACTION ? 0.85 : 0.92;
            double speed = capability == ModelCapabilityType.TRANSCRIPT_EXTRACTION ? 0.75 : 0.6;
            registry.configureCapability(actor, modelKey, new ConfigureCapabilityCommand(
                    capability,
                    quality,
                    speed,
                    4096,
                    true
            ));
        }
    }

    private String resolveServedModelId(URI baseUrl, Set<String> servedModelIds) {
        String discovered = probeModelId(baseUrl);
        if (discovered != null && !discovered.isBlank()) {
            return discovered.trim();
        }
        if (servedModelIds != null) {
            for (String id : servedModelIds) {
                if (id != null && !id.isBlank() && !id.equals("nanobaseai-primary") && !id.equals("nanobaseai-local")) {
                    return id.trim();
                }
            }
        }
        return "nanobase-qwen-local";
    }

    private String probeModelId(URI baseUrl) {
        for (URI probe : List.of(baseUrl.resolve("/health"), baseUrl.resolve("/v1/llm/health"))) {
            try {
                HttpRequest request = HttpRequest.newBuilder(probe)
                        .timeout(Duration.ofSeconds(4))
                        .header("Accept", "application/json")
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    continue;
                }
                JsonNode root = objectMapper.readTree(response.body());
                String model = textOrNull(root.get("model"));
                if (model != null) {
                    return model;
                }
                JsonNode llm = root.path("llm");
                model = textOrNull(llm.get("model"));
                if (model != null) {
                    return model;
                }
                JsonNode models = llm.path("models").path("models");
                if (models.isArray() && !models.isEmpty()) {
                    String fromList = textOrNull(models.get(0).get("model"));
                    if (fromList == null) {
                        fromList = textOrNull(models.get(0).get("name"));
                    }
                    if (fromList != null) {
                        return fromList;
                    }
                }
            } catch (Exception ex) {
                log.debug("NanobaseAI model probe failed uri={} reason={}", probe, ex.getMessage());
            }
        }
        return null;
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static String toModelKey(String servedModelId) {
        String cleaned = servedModelId.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
        if (cleaned.isBlank()) {
            return "nanobaseai-primary";
        }
        return cleaned.startsWith("nanobase") ? cleaned : "nanobaseai-" + cleaned;
    }

    private static String displayName(String servedModelId) {
        return servedModelId.replace('-', ' ');
    }

    private static String hostLabel(URI baseUrl) {
        if (baseUrl.getHost() == null || baseUrl.getHost().isBlank()) {
            return "local";
        }
        return baseUrl.getHost();
    }
}
