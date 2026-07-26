package com.nanobaseai.actenora.security.aiprocessing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * Seeds the model-control registry from configured NanobaseAI Intelligence settings on startup.
 */
public final class NanobaseAiModelRegistryStartupSync implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NanobaseAiModelRegistryStartupSync.class);

    private final NanobaseAiConnectionService connectionService;
    private final LocalProviderProperties properties;

    public NanobaseAiModelRegistryStartupSync(
            NanobaseAiConnectionService connectionService,
            LocalProviderProperties properties
    ) {
        this.connectionService = connectionService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.resolvedKind() == LocalProviderProperties.Kind.MOCK) {
            return;
        }
        try {
            connectionService.testConnection();
            log.info("NanobaseAI model registry startup sync complete endpoint={}", properties.getBaseUrl());
        } catch (RuntimeException ex) {
            log.warn("NanobaseAI model registry startup sync skipped reason={}", ex.getMessage());
        }
    }
}
