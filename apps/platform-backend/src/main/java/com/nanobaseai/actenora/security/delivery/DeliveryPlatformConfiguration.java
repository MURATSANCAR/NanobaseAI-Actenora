package com.nanobaseai.actenora.security.delivery;

import com.nanobaseai.actenora.audit.api.AuditApi;
import com.nanobaseai.actenora.delivery.api.DeliveryApi;
import com.nanobaseai.actenora.delivery.application.port.DeliveryAuditPort;
import com.nanobaseai.actenora.meetingintelligence.application.port.ApprovedNoteFinalDeliveryPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Map;
import java.util.UUID;

/**
 * FAZ 19/20 — Delivery platform composition (AuditApi bridge).
 * Core DeliveryApi / ExternalDeliveryService / DeliveryWorker beans come from
 * DeliveryModuleConfiguration once ApprovalApi is present.
 */
@Configuration
public class DeliveryPlatformConfiguration {

    @Bean
    @Primary
    public DeliveryAuditPort auditBackedDeliveryAuditPort(AuditApi auditApi) {
        return (tenantId, actorId, action, resourceType, resourceId, metadata, occurredAt) ->
                auditApi.append(
                        tenantId,
                        actorId == null ? "system" : actorId,
                        action,
                        resourceType,
                        resourceId == null ? UUID.randomUUID() : resourceId,
                        metadata == null ? Map.of() : metadata,
                        occurredAt
                );
    }

    @Bean
    @ConditionalOnBean(DeliveryApi.class)
    public ApprovedNoteFinalDeliveryPort approvedNoteFinalDeliveryPort(DeliveryApi deliveryApi) {
        return new PlatformApprovedNoteFinalDeliveryAdapter(deliveryApi);
    }
}
