package com.nanobaseai.actenora.modelmanagement.config;

import com.nanobaseai.actenora.modelmanagement.api.ModelManagementApi;
import com.nanobaseai.actenora.modelmanagement.application.DeploymentHealthSettings;
import com.nanobaseai.actenora.modelmanagement.application.ModelControlPermissionPort;
import com.nanobaseai.actenora.modelmanagement.application.ModelDefinitionRepository;
import com.nanobaseai.actenora.modelmanagement.application.ModelDeploymentRepository;
import com.nanobaseai.actenora.modelmanagement.application.ModelRegistryAuditPort;
import com.nanobaseai.actenora.modelmanagement.application.ModelRegistryService;
import com.nanobaseai.actenora.modelmanagement.application.TenantModelAllowlistPort;
import com.nanobaseai.actenora.modelmanagement.infrastructure.ActorPermissionGate;
import com.nanobaseai.actenora.modelmanagement.infrastructure.InMemoryModelDefinitionRepository;
import com.nanobaseai.actenora.modelmanagement.infrastructure.InMemoryModelDeploymentRepository;
import com.nanobaseai.actenora.modelmanagement.infrastructure.InMemoryTenantModelAllowlist;
import com.nanobaseai.actenora.modelmanagement.infrastructure.RecordingModelRegistryAuditPort;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ModelManagementConfiguration {

    @Bean
    @ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "inmemory", matchIfMissing = true)
    @ConditionalOnMissingBean(ModelDefinitionRepository.class)
    ModelDefinitionRepository modelDefinitionRepository() {
        return new InMemoryModelDefinitionRepository();
    }

    @Bean
    @ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "inmemory", matchIfMissing = true)
    @ConditionalOnMissingBean(ModelDeploymentRepository.class)
    ModelDeploymentRepository modelDeploymentRepository() {
        return new InMemoryModelDeploymentRepository();
    }

    @Bean
    @ConditionalOnMissingBean(ModelControlPermissionPort.class)
    ModelControlPermissionPort modelControlPermissionPort() {
        return new ActorPermissionGate();
    }

    /**
     * Default allowlist is empty in-memory. Platform wiring should replace this with
     * a PolicyApi-backed adapter: {@code (tenantId, modelKey) -> policyApi.isModelAllowed(...)}.
     */
    @Bean
    @ConditionalOnMissingBean(TenantModelAllowlistPort.class)
    TenantModelAllowlistPort tenantModelAllowlistPort() {
        return new InMemoryTenantModelAllowlist();
    }

    @Bean
    @ConditionalOnMissingBean(ModelRegistryAuditPort.class)
    ModelRegistryAuditPort modelRegistryAuditPort() {
        return new RecordingModelRegistryAuditPort();
    }

    @Bean
    @ConditionalOnMissingBean(DeploymentHealthSettings.class)
    DeploymentHealthSettings deploymentHealthSettings() {
        return new DeploymentHealthSettings(Duration.ofSeconds(30));
    }

    @Bean
    @ConditionalOnMissingBean(InstantClock.class)
    InstantClock instantClock() {
        return InstantClock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(ModelRegistryService.class)
    ModelRegistryService modelRegistryService(
            ModelDefinitionRepository modelDefinitions,
            ModelDeploymentRepository deployments,
            ModelControlPermissionPort permissions,
            TenantModelAllowlistPort allowlist,
            ModelRegistryAuditPort audit,
            DeploymentHealthSettings healthSettings,
            InstantClock clock
    ) {
        return new ModelRegistryService(
                modelDefinitions,
                deployments,
                permissions,
                allowlist,
                audit,
                healthSettings,
                clock
        );
    }

    @Bean
    @ConditionalOnMissingBean(ModelManagementApi.class)
    ModelManagementApi modelManagementApi(ModelRegistryService service) {
        return new ModelManagementApi.Default(service);
    }
}
