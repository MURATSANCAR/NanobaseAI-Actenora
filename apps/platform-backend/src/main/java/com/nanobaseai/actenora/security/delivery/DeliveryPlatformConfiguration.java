package com.nanobaseai.actenora.security.delivery;

import com.nanobaseai.actenora.audit.api.AuditApi;
import com.nanobaseai.actenora.delivery.api.DeliveryApi;
import com.nanobaseai.actenora.delivery.application.port.DeliveryMailProvider;
import com.nanobaseai.actenora.delivery.application.port.DeliveryAuditPort;
import com.nanobaseai.actenora.delivery.application.port.PdfAttachmentPort;
import com.nanobaseai.actenora.delivery.application.worker.DeliveryWorker;
import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meetingintelligence.api.MeetingIntelligenceApi;
import com.nanobaseai.actenora.meetingintelligence.application.port.ApprovedNoteFinalDeliveryPort;
import com.nanobaseai.actenora.microsoftconnection.api.MicrosoftConnectionApi;
import com.nanobaseai.actenora.security.meeting.MeetingEndedOrganizerMailHandler;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectStorage;
import com.nanobaseai.actenora.template.api.TemplateApi;
import com.nanobaseai.actenora.template.application.DocumentRenderWorker;
import com.nanobaseai.actenora.template.application.port.out.RenderJobRepository;
import com.nanobaseai.actenora.template.application.port.out.RenderedDocumentRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;

import java.util.Map;
import java.util.Optional;
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
    @ConditionalOnProperty(name = "actenora.delivery.mail.provider", havingValue = "microsoft-graph")
    public DeliveryMailProvider graphBackedDeliveryMailProvider(
            MicrosoftConnectionApi microsoft,
            InstantClock clock,
            @Value("${actenora.microsoft-graph.tenant-id:}") String tenantId,
            @Value("${actenora.microsoft-graph.client-id:}") String clientId,
            @Value("${actenora.delivery.mail.graph-sender:}") String senderUpn
    ) {
        return new MicrosoftGraphDeliveryBridge(
                microsoft,
                clock,
                tenantId,
                clientId,
                senderUpn
        );
    }

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

    /**
     * Do not gate with {@code @ConditionalOnBean}: that can evaluate before {@link DeliveryApi}
     * is registered and permanently skip this bean, leaving approval GRANT on a noop final-delivery port.
     * Method parameters already require {@link DeliveryApi} and {@link PdfAttachmentPort}.
     */
    @Bean
    public ApprovedNoteFinalDeliveryPort approvedNoteFinalDeliveryPort(
            DeliveryApi deliveryApi,
            PdfAttachmentPort pdfAttachmentPort,
            ObjectProvider<TemplateApi> templateApi,
            ObjectProvider<DocumentRenderWorker> renderWorker,
            ObjectProvider<RenderJobRepository> renderJobs,
            ObjectProvider<RenderedDocumentRepository> renderedDocuments,
            ObjectProvider<MeetingIntelligenceApi> meetingIntelligenceApi,
            ObjectProvider<ObjectStorage> objectStorage,
            ObjectProvider<MeetingApi> meetingApi,
            ObjectProvider<DeliveryWorker> deliveryWorker
    ) {
        return new PlatformApprovedNoteFinalDeliveryAdapter(
                deliveryApi,
                pdfAttachmentPort,
                Optional.ofNullable(templateApi.getIfAvailable()),
                Optional.ofNullable(renderWorker.getIfAvailable()),
                Optional.ofNullable(renderJobs.getIfAvailable()),
                Optional.ofNullable(renderedDocuments.getIfAvailable()),
                Optional.ofNullable(meetingIntelligenceApi.getIfAvailable()),
                Optional.ofNullable(objectStorage.getIfAvailable()),
                Optional.ofNullable(meetingApi.getIfAvailable()),
                Optional.ofNullable(deliveryWorker.getIfAvailable())
        );
    }

    @Bean
    @ConditionalOnBean({MeetingApi.class, DeliveryApi.class})
    public MeetingEndedOrganizerMailHandler meetingEndedOrganizerMailHandler(
            MeetingApi meetingApi,
            DeliveryApi deliveryApi,
            ObjectProvider<DeliveryWorker> deliveryWorker,
            @org.springframework.beans.factory.annotation.Value(
                    "${actenora.delivery.portal-link.base-url:https://portal.nanobase.ai/easymeeting}")
                    String portalBaseUrl
    ) {
        return new MeetingEndedOrganizerMailHandler(
                meetingApi,
                deliveryApi,
                Optional.ofNullable(deliveryWorker.getIfAvailable()),
                portalBaseUrl
        );
    }
}
