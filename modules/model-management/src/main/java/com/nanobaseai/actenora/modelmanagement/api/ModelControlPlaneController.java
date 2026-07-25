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
import com.nanobaseai.actenora.sharedkernel.security.AuthenticatedPrincipal;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.List;

/**
 * HTTP control-plane endpoints for model / capability / deployment registry.
 * Actor is resolved from {@link TenantSecurityContext} (FAZ 4); requires {@code MODEL_CONTROL}.
 */
@RestController
@RequestMapping("/api/v1/model-control")
public class ModelControlPlaneController {

    public static final String MODEL_CONTROL_PERMISSION = "MODEL_CONTROL";

    private final ModelManagementApi api;

    public ModelControlPlaneController(ModelManagementApi api) {
        this.api = api;
    }

    @PostMapping("/models")
    public ModelDefinitionView register(@RequestBody RegisterModelRequest body) {
        return api.registerModel(requireActor(), body.toCommand());
    }

    @PutMapping("/models/{modelKey}")
    public ModelDefinitionView update(
            @PathVariable String modelKey,
            @RequestBody UpdateModelRequest body
    ) {
        return api.updateModel(requireActor(), modelKey, body.toCommand());
    }

    @GetMapping("/models/{modelKey}")
    public ModelDefinitionView get(@PathVariable String modelKey) {
        requireActor();
        return api.getModel(modelKey);
    }

    @PutMapping("/models/{modelKey}/capabilities/{capability}")
    public ModelDefinitionView configureCapability(
            @PathVariable String modelKey,
            @PathVariable ModelCapabilityType capability,
            @RequestBody ConfigureCapabilityRequest body
    ) {
        return api.configureCapability(
                requireActor(),
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
    public ModelDefinitionView enable(@PathVariable String modelKey) {
        return api.enableModel(requireActor(), modelKey);
    }

    @PostMapping("/models/{modelKey}/disable")
    public ModelDefinitionView disable(@PathVariable String modelKey) {
        return api.disableModel(requireActor(), modelKey);
    }

    @PostMapping("/models/{modelKey}/drain")
    public ModelDefinitionView drain(@PathVariable String modelKey) {
        return api.drainModel(requireActor(), modelKey);
    }

    @PostMapping("/deployments")
    public ModelDeploymentView registerDeployment(@RequestBody RegisterDeploymentRequest body) {
        return api.registerDeployment(requireActor(), body.toCommand());
    }

    @PostMapping("/deployments/{deploymentKey}/heartbeat")
    public ModelDeploymentView heartbeat(@PathVariable String deploymentKey) {
        return api.heartbeat(requireActor(), deploymentKey);
    }

    @GetMapping("/health")
    public ModelHealthView health() {
        return api.healthView(requireActor());
    }

    @ExceptionHandler(ModelRegistryException.class)
    public ResponseEntity<ProblemDetail> handleRegistry(ModelRegistryException ex) {
        HttpStatus status = switch (ex.code()) {
            case "DUPLICATE_MODEL_KEY", "DUPLICATE_DEPLOYMENT_KEY" -> HttpStatus.CONFLICT;
            case "MODEL_NOT_FOUND", "DEPLOYMENT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "PERMISSION_DENIED" -> HttpStatus.FORBIDDEN;
            case "INVALID_CONTEXT_SIZE", "INVALID_MODEL_STATE", "MODEL_NOT_ALLOWED_FOR_TENANT",
                 "CLOUD_PROVIDER_REJECTED" -> HttpStatus.UNPROCESSABLE_ENTITY;
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

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleUnauthenticated(IllegalStateException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("UNAUTHENTICATED");
        problem.setProperty("code", "UNAUTHENTICATED");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    static ActorPrincipal requireActor() {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        if (!principal.hasPermission(MODEL_CONTROL_PERMISSION)) {
            throw ModelRegistryException.permissionDenied(MODEL_CONTROL_PERMISSION);
        }
        String role = principal.roles().stream().findFirst().orElse("OPERATIONS");
        return ActorPrincipal.of(principal.userId(), role, EnumSet.allOf(ModelControlPermission.class));
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
        public RegisterModelCommand toCommand() {
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
        public UpdateModelCommand toCommand() {
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
        public RegisterDeploymentCommand toCommand() {
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
