package com.nanobaseai.actenora.security.operations;

import com.nanobaseai.actenora.microsoftconnection.infrastructure.config.MicrosoftGraphSpringProperties;
import com.nanobaseai.actenora.operations.infrastructure.InMemoryOpsTelemetryPort;
import com.nanobaseai.actenora.security.aiprocessing.NanobaseAiConnectionService;
import com.nanobaseai.actenora.security.microsoftconnection.TeamsTranscriptPollScheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class PlatformOperationsConfiguration {

    @Bean
    PlatformOpsWorkerTelemetryReporter platformOpsWorkerTelemetryReporter(
            InMemoryOpsTelemetryPort telemetry,
            ObjectProvider<TeamsTranscriptPollScheduler> transcriptPollScheduler,
            ObjectProvider<NanobaseAiConnectionService> intelligenceConnection,
            ObjectProvider<MicrosoftGraphSpringProperties> graphProperties,
            @Value("${actenora.ai.worker.enabled:true}") boolean aiWorkerEnabled,
            @Value("${actenora.microsoft-graph.mailbox-sync-enabled:true}") boolean mailboxSyncEnabled
    ) {
        return new PlatformOpsWorkerTelemetryReporter(
                telemetry,
                transcriptPollScheduler,
                intelligenceConnection,
                graphProperties.getIfAvailable() == null ? new MicrosoftGraphSpringProperties() : graphProperties.getObject(),
                aiWorkerEnabled,
                mailboxSyncEnabled
        );
    }
}
