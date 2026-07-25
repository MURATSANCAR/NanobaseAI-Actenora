package com.nanobaseai.actenora.modelmanagement.api;

import com.nanobaseai.actenora.modelmanagement.application.ActorPrincipal;
import com.nanobaseai.actenora.modelmanagement.application.ConfigureCapabilityCommand;
import com.nanobaseai.actenora.modelmanagement.application.ModelControlPermission;
import com.nanobaseai.actenora.modelmanagement.application.ModelDefinitionView;
import com.nanobaseai.actenora.modelmanagement.application.ModelDeploymentView;
import com.nanobaseai.actenora.modelmanagement.application.ModelHealthView;
import com.nanobaseai.actenora.modelmanagement.application.RegisterDeploymentCommand;
import com.nanobaseai.actenora.modelmanagement.application.RegisterModelCommand;
import com.nanobaseai.actenora.modelmanagement.application.UpdateModelCommand;
import com.nanobaseai.actenora.modelmanagement.domain.ModelCapabilityType;
import com.nanobaseai.actenora.modelmanagement.domain.ModelRegistryException;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * HTTP control-plane endpoints for model / capability / deployment registry.
 * Actor headers are temporary until FAZ 4 Identity filters own principal resolution.
 */
@RestController
@RequestMapping("/api/v1/model-control")
public class ModelControlPlaneController {

    private final ModelManagementApi api;

    public ModelControlPlaneController(ModelManagementApi api) {
        this.api = api;
    }

    @PostMapping("/models")
    public ModelDefinitionView register(
            @RequestHeader("X-Actor-User-Id") UUID userId,
            @RequestHeader(value = "X-Actor-Role", defaultValue = "OPERATIONS") String role,
            @RequestBody RegisterModelRequest body
    ) {
        return api.registerModel(actor(userId, role), body.toCommand());
    }

    @PutMapping("/models/{modelKey}")
    public ModelDefinitionView update(
            @RequestHeader("X-Actor-User-Id") UUID userId,
            @RequestHeader(value = "X-Actor-Role", defaultValue = "OPERATIONS") String role,
            @PathVariable String modelKey,
            @RequestBody UpdateModelRequest body
    ) {
        return api.updateModel(actor(userId, role), modelKey, body.toCommand());
    }

    @PutMapping("/models/{modelKey}/capabilities/{capability}")
    public ModelDefinitionView configureCapability(
            @RequestHeader("X-Actor-User-Id") UUID userId,
            @RequestHeader(value = "X-Actor-Role", defaultValue = "OPERATIONS") String role,
            @PathVariable String modelKey,
            @PathVariable ModelCapabilityType capability,
            @RequestBody ConfigureCapabilityRequest body
    ) {
        return api.configureCapability(
                actor(userId, role),
                modelKey,
                new ConfigureCapabilityCommand(
                        capability,
                        body.qualityScore(),
                        body.speedScore(),
                        body.minContextRequired(),
                        body.enabled()
                )
        );
    }

    @PostMapping("/models/{modelKey}/enable")
    public ModelDefinitionView enable(
            @RequestHeader("X-Actor-User-Id") UUID userId,
            @RequestHeader(value = "X-Actor-Role", defaultValue = "OPERATIONS") String role,
            @PathVariable String modelKey
    ) {
        return api.enableModel(actor(userId, role), modelKey);
    }

    @PostMapping("/models/{modelKey}/disable")
    public ModelDefinitionView disable(
            @RequestHeader("X-Actor-User-Id") UUID userId,
            @RequestHeader(value = "X-Actor-Role", defaultValue = "OPERATIONS") String role,
            @PathVariable String modelKey
    ) {
        return api.disableModel(actor(userId, role), modelKey);
    }

    @PostMapping("/models/{modelKey}/drain")
    public ModelDefinitionView drain(
            @RequestHeader("X-Actor-User-Id") UUID userId,
            @RequestHeader(value = "X-Actor-Role", defaultValue = "OPERATIONS") String role,
            @PathVariable String modelKey
    ) {
        return api.drainModel(actor(userId, role), modelKey);
    }

    @PostMapping("/deployments")
    public ModelDeploymentView registerDeployment(
            @RequestHeader("X-Actor-User-Id") UUID userId,
            @RequestHeader(value = "X-Actor-Role", defaultValue = "OPERATIONS") String role,
            @RequestBody RegisterDeploymentRequest body
    ) {
        return api.registerDeployment(actor(userId, role), body.toCommand());
    }

    @PostMapping("/deployments/{deploymentKey}/heartbeat")
    public ModelDeploymentView heartbeat(
            @RequestHeader("X-Actor-User-Id") UUID userId,
            @RequestHeader(value = "X-Actor-Role", defaultValue = "OPERATIONS") String role,
            @PathVariable String deploymentKey
    ) {
        return api.heartbeat(actor(userId, role), deploymentKey);
    }

    @GetMapping("/health")
    public ModelHealthView health(
            @RequestHeader("X-Actor-User-Id") UUID userId,
            @RequestHeader(value = "X-Actor-Role", defaultValue = "OPERATIONS") String role
    ) {
        return api.healthView(actor(userId, role));
    }

    @ExceptionHandler(ModelRegistryException.class)
    public ResponseEntity<ProblemDetail> handleRegistry(ModelRegistryException ex) {
        HttpStatus status = switch (ex.code()) {
            case "DUPLICATE_MODEL_KEY", "DUPLICATE_DEPLOYMENT_KEY" -> HttpStatus.CONFLICT;
            case "MODEL_NOT_FOUND", "DEPLOYMENT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "PERMISSION_DENIED" -> HttpStatus.FORBIDDEN;
            case "INVALID_CONTEXT_SIZE", "INVALID_MODEL_STATE", "MODEL_NOT_ALLOWED_FOR_TENANT" ->
                    HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.BAD_REQUEST;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle(ex.code());
        problem.setProperty("code", ex.code());
        return ResponseEntity.status(status).body(problem);
    }

    @ExceptionHandler(ActenoraException.class)
    public ResponseEntity<ProblemDetail> handleActenora(ActenoraException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle(ex.code());
        problem.setProperty("code", ex.code());
        return ResponseEntity.badRequest().body(problem);
    }

    private static ActorPrincipal actor(UUID userId, String role) {
        Set<ModelControlPermission> permissions = "OPERATIONS".equalsIgnoreCase(role)
                || "SUPER_ADMIN".equalsIgnoreCase(role)
                ? EnumSet.allOf(ModelControlPermission.class)
                : Set.of(ModelControlPermission.HEALTH_VIEW, ModelControlPermission.DEPLOYMENT_HEARTBEAT);
        return ActorPrincipal.of(userId, role, permissions);
    }

    public record RegisterModelRequest(
            String modelKey,
            String displayName,
            String providerType,
            String servedModelId,
            String modelFamily,
            String parameterSize,
            String quantization,
            int contextWindow,
            int maxOutputTokens,
            List<String> supportedLanguages,
            int priority,
            double qualityScore,
            double speedScore
    ) {
        RegisterModelCommand toCommand() {
            return new RegisterModelCommand(
                    modelKey,
                    displayName,
                    providerType,
                    servedModelId,
                    modelFamily,
                    parameterSize,
                    quantization,
                    contextWindow,
                    maxOutputTokens,
                    supportedLanguages,
                    priority,
                    qualityScore,
                    speedScore
            );
        }
    }

    public record UpdateModelRequest(
            String displayName,
            String providerType,
            String servedModelId,
            String modelFamily,
            String parameterSize,
            String quantization,
            int contextWindow,
            int maxOutputTokens,
            List<String> supportedLanguages,
            Integer priority,
            Double qualityScore,
            Double speedScore
    ) {
        UpdateModelCommand toCommand() {
            return new UpdateModelCommand(
                    displayName,
                    providerType,
                    servedModelId,
                    modelFamily,
                    parameterSize,
                    quantization,
                    contextWindow,
                    maxOutputTokens,
                    supportedLanguages,
                    priority,
                    qualityScore,
                    speedScore
            );
        }
    }

    public record ConfigureCapabilityRequest(
            double qualityScore,
            double speedScore,
            int minContextRequired,
            boolean enabled
    ) {
    }

    public record RegisterDeploymentRequest(
            String modelKey,
            String deploymentKey,
            String endpoint,
            String nodeName,
            String zone,
            String hardwareType,
            String gpuType,
            int gpuCount,
            int cpuCount,
            int memoryGb,
            int maxConcurrency
    ) {
        RegisterDeploymentCommand toCommand() {
            return new RegisterDeploymentCommand(
                    modelKey,
                    deploymentKey,
                    endpoint,
                    nodeName,
                    zone,
                    hardwareType,
                    gpuType,
                    gpuCount,
                    cpuCount,
                    memoryGb,
                    maxConcurrency
            );
        }
    }
}
