package com.nanobaseai.actenora.delivery.infrastructure.config;

import com.nanobaseai.actenora.delivery.application.port.DeliveryOrderRepository;
import com.nanobaseai.actenora.delivery.application.port.DeliveryRequestRepository;
import com.nanobaseai.actenora.delivery.infrastructure.persistence.JdbcDeliveryOrderRepository;
import com.nanobaseai.actenora.delivery.infrastructure.persistence.JdbcDeliveryRequestRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "jdbc")
public class DeliveryJdbcPersistenceConfiguration {

    @Bean
    DeliveryOrderRepository deliveryOrderRepository(DataSource dataSource) {
        return new JdbcDeliveryOrderRepository(new JdbcTemplate(dataSource));
    }

    @Bean
    DeliveryRequestRepository deliveryRequestRepository(DataSource dataSource) {
        return new JdbcDeliveryRequestRepository(new JdbcTemplate(dataSource));
    }
}
