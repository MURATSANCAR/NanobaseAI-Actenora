package com.nanobaseai.actenora.platform.extraction.transcript;

import com.nanobaseai.actenora.transcript.api.TranscriptDeploymentMode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Dual-publish / cutover wiring: when mode=remote, platform proxies transcript HTTP
 * to the extracted worker and does not load embedded transcript beans.
 */
@Configuration
@EnableConfigurationProperties(TranscriptRemoteProperties.class)
@ConditionalOnProperty(
        name = TranscriptDeploymentMode.PROPERTY,
        havingValue = TranscriptDeploymentMode.REMOTE)
public class TranscriptExtractionConfiguration {

    @Bean
    TranscriptRemoteClient transcriptRemoteClient(TranscriptRemoteProperties properties) {
        return new TranscriptRemoteClient(properties);
    }
}
