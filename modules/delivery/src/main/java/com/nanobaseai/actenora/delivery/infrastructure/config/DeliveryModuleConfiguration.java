package com.nanobaseai.actenora.delivery.infrastructure.config;

import com.nanobaseai.actenora.approval.api.ApprovalApi;
import com.nanobaseai.actenora.delivery.api.DeliveryApi;
import com.nanobaseai.actenora.delivery.application.DeliveryDispatcherService;
import com.nanobaseai.actenora.delivery.application.ExternalDeliveryService;
import com.nanobaseai.actenora.delivery.application.port.DeliveryAuditPort;
import com.nanobaseai.actenora.delivery.application.port.DeliveryMailProvider;
import com.nanobaseai.actenora.delivery.application.port.DeliveryRateLimiter;
import com.nanobaseai.actenora.delivery.application.port.DeliveryRequestRepository;
import com.nanobaseai.actenora.delivery.application.port.NoteApprovalGatePort;
import com.nanobaseai.actenora.delivery.application.port.PdfAttachmentPort;
import com.nanobaseai.actenora.delivery.application.port.SignedPortalLinkPort;
import com.nanobaseai.actenora.delivery.application.worker.DeliveryWorker;
import com.nanobaseai.actenora.delivery.infrastructure.DeliveryApiAdapter;
import com.nanobaseai.actenora.delivery.infrastructure.approval.ApprovalApiNoteApprovalGate;
import com.nanobaseai.actenora.delivery.infrastructure.approval.InMemoryNoteApprovalGate;
import com.nanobaseai.actenora.delivery.infrastructure.audit.RecordingDeliveryAuditPort;
import com.nanobaseai.actenora.delivery.infrastructure.mail.MailHogMailProvider;
import com.nanobaseai.actenora.delivery.infrastructure.pdf.InMemoryPdfAttachmentPort;
import com.nanobaseai.actenora.delivery.infrastructure.persistence.InMemoryDeliveryRequestRepository;
import com.nanobaseai.actenora.delivery.infrastructure.portal.HmacSignedPortalLinkService;
import com.nanobaseai.actenora.delivery.infrastructure.ratelimit.FixedWindowDeliveryRateLimiter;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Delivery module wiring. Worker beans stay extractable into {@code services/delivery-worker}.
 */
@Configuration
public class DeliveryModuleConfiguration {

    @Bean
    @ConditionalOnMissingBean
    DeliveryRequestRepository deliveryRequestRepository() {
        return new InMemoryDeliveryRequestRepository();
    }

    @Bean
    @ConditionalOnMissingBean(NoteApprovalGatePort.class)
    @ConditionalOnBean(ApprovalApi.class)
    NoteApprovalGatePort approvalApiNoteApprovalGate(ApprovalApi approvalApi) {
        return new ApprovalApiNoteApprovalGate(approvalApi);
    }

    @Bean
    @ConditionalOnMissingBean(NoteApprovalGatePort.class)
    NoteApprovalGatePort inMemoryNoteApprovalGate() {
        return new InMemoryNoteApprovalGate();
    }

    @Bean
    @ConditionalOnMissingBean
    DeliveryMailProvider deliveryMailProvider(InstantClock clock) {
        return MailHogMailProvider.localDefaults(clock::now);
    }

    @Bean
    @ConditionalOnMissingBean
    DeliveryRateLimiter deliveryRateLimiter(InstantClock clock) {
        return new FixedWindowDeliveryRateLimiter(60, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    PdfAttachmentPort pdfAttachmentPort() {
        return new InMemoryPdfAttachmentPort();
    }

    @Bean
    @ConditionalOnMissingBean
    SignedPortalLinkPort signedPortalLinkPort() {
        return new HmacSignedPortalLinkService(
                "actenora-local-portal-secret",
                "https://portal.nanobase.ai"
        );
    }

    @Bean
    @ConditionalOnMissingBean
    DeliveryAuditPort deliveryAuditPort() {
        return new RecordingDeliveryAuditPort();
    }

    @Bean
    @ConditionalOnMissingBean
    InstantClock deliveryInstantClock() {
        return InstantClock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    Clock deliveryClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    DeliveryDispatcherService deliveryDispatcherService(
            DeliveryRequestRepository repository,
            NoteApprovalGatePort approvalGate,
            DeliveryMailProvider mailProvider,
            DeliveryRateLimiter rateLimiter,
            PdfAttachmentPort pdfAttachmentPort,
            SignedPortalLinkPort signedPortalLinkPort,
            DeliveryAuditPort auditPort,
            InstantClock clock
    ) {
        return new DeliveryDispatcherService(
                repository,
                approvalGate,
                mailProvider,
                rateLimiter,
                pdfAttachmentPort,
                signedPortalLinkPort,
                auditPort,
                clock
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ApprovalApi.class)
    ExternalDeliveryService externalDeliveryService(ApprovalApi approvalApi, Clock clock) {
        return new ExternalDeliveryService(approvalApi, clock);
    }

    @Bean
    @ConditionalOnMissingBean(DeliveryApi.class)
    @ConditionalOnBean(ExternalDeliveryService.class)
    DeliveryApi deliveryApi(
            ExternalDeliveryService externalDeliveryService,
            DeliveryDispatcherService dispatcher,
            DeliveryRequestRepository repository
    ) {
        return new DeliveryApiAdapter(externalDeliveryService, dispatcher, repository);
    }

    @Bean
    @ConditionalOnMissingBean
    DeliveryWorker deliveryWorker(
            DeliveryRequestRepository repository,
            DeliveryDispatcherService dispatcher,
            InstantClock clock
    ) {
        return new DeliveryWorker(repository, dispatcher, clock, 32);
    }
}
