package com.nanobaseai.actenora.modelmanagement.api;

import com.nanobaseai.actenora.modelmanagement.application.ActorPrincipal;
import com.nanobaseai.actenora.modelmanagement.application.ConfigureCapabilityCommand;
import com.nanobaseai.actenora.modelmanagement.application.ModelDefinitionView;
import com.nanobaseai.actenora.modelmanagement.application.ModelDeploymentView;
import com.nanobaseai.actenora.modelmanagement.application.ModelHealthView;
import com.nanobaseai.actenora.modelmanagement.application.ModelRegistryService;
import com.nanobaseai.actenora.modelmanagement.application.RegisterDeploymentCommand;
import com.nanobaseai.actenora.modelmanagement.application.RegisterModelCommand;
import com.nanobaseai.actenora.modelmanagement.application.UpdateModelCommand;

import java.util.Objects;
import java.util.UUID;

/**
 * Public façade for the Model Management bounded context.
 * Cross-module callers use types in this package only.
 */
public interface ModelManagementApi {

    ModelDefinitionView registerModel(ActorPrincipal actor, RegisterModelCommand command);

    ModelDefinitionView updateModel(ActorPrincipal actor, String modelKey, UpdateModelCommand command);

    ModelDefinitionView configureCapability(
            ActorPrincipal actor,
            String modelKey,
            ConfigureCapabilityCommand command
    );

    ModelDefinitionView enableModel(ActorPrincipal actor, String modelKey);

    ModelDefinitionView disableModel(ActorPrincipal actor, String modelKey);

    ModelDefinitionView drainModel(ActorPrincipal actor, String modelKey);

    ModelDeploymentView registerDeployment(ActorPrincipal actor, RegisterDeploymentCommand command);

    ModelDeploymentView heartbeat(ActorPrincipal actor, String deploymentKey);

    ModelHealthView healthView(ActorPrincipal actor);

    ModelDefinitionView getModel(String modelKey);

    boolean isTenantCompatible(UUID tenantId, String modelKey);

    void assertTenantCompatible(UUID tenantId, String modelKey);

    /**
     * Default façade wrapping {@link ModelRegistryService}.
     */
    final class Default implements ModelManagementApi {

        private final ModelRegistryService service;

        public Default(ModelRegistryService service) {
            this.service = Objects.requireNonNull(service, "service");
        }

        @Override
        public ModelDefinitionView registerModel(ActorPrincipal actor, RegisterModelCommand command) {
            return service.registerModel(actor, command);
        }

        @Override
        public ModelDefinitionView updateModel(ActorPrincipal actor, String modelKey, UpdateModelCommand command) {
            return service.updateModel(actor, modelKey, command);
        }

        @Override
        public ModelDefinitionView configureCapability(
                ActorPrincipal actor,
                String modelKey,
                ConfigureCapabilityCommand command
        ) {
            return service.configureCapability(actor, modelKey, command);
        }

        @Override
        public ModelDefinitionView enableModel(ActorPrincipal actor, String modelKey) {
            return service.enableModel(actor, modelKey);
        }

        @Override
        public ModelDefinitionView disableModel(ActorPrincipal actor, String modelKey) {
            return service.disableModel(actor, modelKey);
        }

        @Override
        public ModelDefinitionView drainModel(ActorPrincipal actor, String modelKey) {
            return service.drainModel(actor, modelKey);
        }

        @Override
        public ModelDeploymentView registerDeployment(ActorPrincipal actor, RegisterDeploymentCommand command) {
            return service.registerDeployment(actor, command);
        }

        @Override
        public ModelDeploymentView heartbeat(ActorPrincipal actor, String deploymentKey) {
            return service.heartbeat(actor, deploymentKey);
        }

        @Override
        public ModelHealthView healthView(ActorPrincipal actor) {
            return service.healthView(actor);
        }

        @Override
        public ModelDefinitionView getModel(String modelKey) {
            return service.getModel(modelKey);
        }

        @Override
        public boolean isTenantCompatible(UUID tenantId, String modelKey) {
            return service.isTenantCompatible(tenantId, modelKey);
        }

        @Override
        public void assertTenantCompatible(UUID tenantId, String modelKey) {
            service.assertTenantCompatible(tenantId, modelKey);
        }
    }
}
