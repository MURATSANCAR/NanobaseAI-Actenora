package com.nanobaseai.actenora.security.storage;

import com.nanobaseai.actenora.meetingintelligence.application.port.ArtifactMetadataStorePort;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectStorage;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wraps the active {@link ObjectStorage} so every put registers artifact_metadata.
 */
@Configuration
public class ArtifactMetadataStorageConfiguration {

    @Bean
    static BeanPostProcessor metadataRecordingObjectStoragePostProcessor(
            ObjectProvider<ArtifactMetadataStorePort> metadataStore
    ) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (!(bean instanceof ObjectStorage storage)
                        || bean instanceof MetadataRecordingObjectStorage) {
                    return bean;
                }
                ArtifactMetadataStorePort store = metadataStore.getIfAvailable();
                if (store == null) {
                    return bean;
                }
                return new MetadataRecordingObjectStorage(storage, store, Clock.systemUTC());
            }
        };
    }
}
