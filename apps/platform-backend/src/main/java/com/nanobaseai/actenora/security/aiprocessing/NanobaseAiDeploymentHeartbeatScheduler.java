package com.nanobaseai.actenora.security.aiprocessing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Keeps the model-control deployment heartbeat fresh while NanobaseAI Intelligence is healthy.
 * Registry admissions fail closed after {@link com.nanobaseai.actenora.modelmanagement.application.DeploymentHealthSettings}
 * timeout without this refresh.
 */
public final class NanobaseAiDeploymentHeartbeatScheduler {

    private static final Logger log = LoggerFactory.getLogger(NanobaseAiDeploymentHeartbeatScheduler.class);

    private final NanobaseAiConnectionService connectionService;

    public NanobaseAiDeploymentHeartbeatScheduler(NanobaseAiConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @Scheduled(fixedDelayString = "${actenora.ai.deployment-heartbeat-interval:PT20S}", initialDelayString = "PT10S")
    void refreshHealthyDeploymentHeartbeat() {
        try {
            NanobaseAiConnectionService.ConnectionView view = connectionService.current();
            if (!view.enabled() || !view.healthy()) {
                return;
            }
            connectionService.testConnection();
        } catch (RuntimeException ex) {
            log.debug("NanobaseAI deployment heartbeat skipped reason={}", ex.getMessage());
        }
    }
}
