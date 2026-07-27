package com.nanobaseai.actenora.security.aiprocessing;

import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Prefetch-tuned listener factories for AI stage queues (plan §5).
 * LLM stages: prefetch 1. Short deterministic stages: higher prefetch.
 */
@Configuration
@ConditionalOnProperty(name = "actenora.messaging.mode", havingValue = "jdbc-rabbit")
public class AiStageRabbitListenerConfiguration {

    @Bean
    SimpleRabbitListenerContainerFactory aiLlmStageListenerFactory(ConnectionFactory connectionFactory) {
        return factory(connectionFactory, 1);
    }

    @Bean
    SimpleRabbitListenerContainerFactory aiFastStageListenerFactory(ConnectionFactory connectionFactory) {
        return factory(connectionFactory, 4);
    }

    @Bean
    SimpleRabbitListenerContainerFactory aiParserStageListenerFactory(ConnectionFactory connectionFactory) {
        return factory(connectionFactory, 10);
    }

    private static SimpleRabbitListenerContainerFactory factory(ConnectionFactory connectionFactory, int prefetch) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setPrefetchCount(prefetch);
        factory.setDefaultRequeueRejected(false);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.AUTO);
        return factory;
    }
}
