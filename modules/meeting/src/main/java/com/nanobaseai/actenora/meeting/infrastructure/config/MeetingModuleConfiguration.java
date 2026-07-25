package com.nanobaseai.actenora.meeting.infrastructure.config;

import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meeting.api.collaboration.MeetingCollaborationApi;
import com.nanobaseai.actenora.meeting.application.BusinessContextApplicationService;
import com.nanobaseai.actenora.meeting.application.MeetingApiFacade;
import com.nanobaseai.actenora.meeting.application.MeetingApplicationService;
import com.nanobaseai.actenora.meeting.application.collaboration.MeetingCollaborationService;
import com.nanobaseai.actenora.meeting.application.collaboration.MeetingMembershipGuard;
import com.nanobaseai.actenora.meeting.application.collaboration.port.CollaborationIdempotencyStore;
import com.nanobaseai.actenora.meeting.application.collaboration.port.MeetingAgendaRepository;
import com.nanobaseai.actenora.meeting.application.collaboration.port.MeetingAppTokenValidator;
import com.nanobaseai.actenora.meeting.application.collaboration.port.MeetingMarkerRepository;
import com.nanobaseai.actenora.meeting.application.collaboration.port.OpenTaskRepository;
import com.nanobaseai.actenora.meeting.application.collaboration.port.PrivateNoteRepository;
import com.nanobaseai.actenora.meeting.application.collaboration.port.SharedNoteRepository;
import com.nanobaseai.actenora.meeting.application.port.BusinessContextRepository;
import com.nanobaseai.actenora.meeting.application.port.ClockPort;
import com.nanobaseai.actenora.meeting.application.port.MeetingAuditPort;
import com.nanobaseai.actenora.meeting.application.port.MeetingEventPublisher;
import com.nanobaseai.actenora.meeting.application.port.MeetingOccurrenceRepository;
import com.nanobaseai.actenora.meeting.application.port.MeetingParticipantRepository;
import com.nanobaseai.actenora.meeting.application.port.MeetingSeriesRepository;
import com.nanobaseai.actenora.meeting.application.port.TenantContextPort;
import com.nanobaseai.actenora.meeting.infrastructure.audit.InMemoryMeetingAuditPort;
import com.nanobaseai.actenora.meeting.infrastructure.collaboration.HmacMeetingAppTokenValidator;
import com.nanobaseai.actenora.meeting.infrastructure.collaboration.InMemoryCollaborationIdempotencyStore;
import com.nanobaseai.actenora.meeting.infrastructure.collaboration.InMemoryMeetingAgendaRepository;
import com.nanobaseai.actenora.meeting.infrastructure.collaboration.InMemoryMeetingMarkerRepository;
import com.nanobaseai.actenora.meeting.infrastructure.collaboration.InMemoryOpenTaskRepository;
import com.nanobaseai.actenora.meeting.infrastructure.collaboration.InMemoryPrivateNoteRepository;
import com.nanobaseai.actenora.meeting.infrastructure.collaboration.InMemorySharedNoteRepository;
import com.nanobaseai.actenora.meeting.infrastructure.messaging.InMemoryMeetingEventPublisher;
import com.nanobaseai.actenora.meeting.infrastructure.persistence.InMemoryBusinessContextRepository;
import com.nanobaseai.actenora.meeting.infrastructure.persistence.InMemoryMeetingOccurrenceRepository;
import com.nanobaseai.actenora.meeting.infrastructure.persistence.InMemoryMeetingParticipantRepository;
import com.nanobaseai.actenora.meeting.infrastructure.persistence.InMemoryMeetingSeriesRepository;
import com.nanobaseai.actenora.meeting.infrastructure.tenancy.FixedTenantContext;
import com.nanobaseai.actenora.meeting.infrastructure.time.SystemClockPort;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class MeetingModuleConfiguration {

    @Bean
    @ConditionalOnMissingBean(BusinessContextRepository.class)
    BusinessContextRepository businessContextRepository() {
        return new InMemoryBusinessContextRepository();
    }

    @Bean
    @ConditionalOnMissingBean(MeetingSeriesRepository.class)
    MeetingSeriesRepository meetingSeriesRepository() {
        return new InMemoryMeetingSeriesRepository();
    }

    @Bean
    @ConditionalOnMissingBean(MeetingOccurrenceRepository.class)
    MeetingOccurrenceRepository meetingOccurrenceRepository() {
        return new InMemoryMeetingOccurrenceRepository();
    }

    @Bean
    @ConditionalOnMissingBean(MeetingParticipantRepository.class)
    MeetingParticipantRepository meetingParticipantRepository() {
        return new InMemoryMeetingParticipantRepository();
    }

    @Bean
    @ConditionalOnMissingBean(MeetingEventPublisher.class)
    MeetingEventPublisher meetingEventPublisher() {
        return new InMemoryMeetingEventPublisher();
    }

    @Bean
    @ConditionalOnMissingBean(MeetingAuditPort.class)
    MeetingAuditPort meetingAuditPort() {
        return new InMemoryMeetingAuditPort();
    }

    @Bean
    @ConditionalOnMissingBean(ClockPort.class)
    ClockPort clockPort() {
        return new SystemClockPort();
    }

    @Bean
    @ConditionalOnMissingBean(FixedTenantContext.class)
    FixedTenantContext fixedTenantContext() {
        return new FixedTenantContext(TenantId.random(), UUID.randomUUID());
    }

    @Bean
    @ConditionalOnMissingBean(TenantContextPort.class)
    TenantContextPort tenantContextPort(FixedTenantContext fixedTenantContext) {
        return fixedTenantContext;
    }

    @Bean
    @ConditionalOnMissingBean(MeetingMarkerRepository.class)
    MeetingMarkerRepository meetingMarkerRepository() {
        return new InMemoryMeetingMarkerRepository();
    }

    @Bean
    @ConditionalOnMissingBean(SharedNoteRepository.class)
    SharedNoteRepository sharedNoteRepository() {
        return new InMemorySharedNoteRepository();
    }

    @Bean
    @ConditionalOnMissingBean(PrivateNoteRepository.class)
    PrivateNoteRepository privateNoteRepository() {
        return new InMemoryPrivateNoteRepository();
    }

    @Bean
    @ConditionalOnMissingBean(MeetingAgendaRepository.class)
    MeetingAgendaRepository meetingAgendaRepository() {
        return new InMemoryMeetingAgendaRepository();
    }

    @Bean
    @ConditionalOnMissingBean(OpenTaskRepository.class)
    OpenTaskRepository openTaskRepository() {
        return new InMemoryOpenTaskRepository();
    }

    @Bean
    @ConditionalOnMissingBean(CollaborationIdempotencyStore.class)
    CollaborationIdempotencyStore collaborationIdempotencyStore() {
        return new InMemoryCollaborationIdempotencyStore();
    }

    @Bean
    @ConditionalOnMissingBean(MeetingAppTokenValidator.class)
    MeetingAppTokenValidator meetingAppTokenValidator(
            @Value("${actenora.meeting-app.token-secret:local-dev-meeting-app-secret}") String secret
    ) {
        return new HmacMeetingAppTokenValidator(secret);
    }

    @Bean
    MeetingMembershipGuard meetingMembershipGuard(
            MeetingOccurrenceRepository meetingOccurrenceRepository,
            MeetingParticipantRepository meetingParticipantRepository
    ) {
        return new MeetingMembershipGuard(meetingOccurrenceRepository, meetingParticipantRepository);
    }

    @Bean
    MeetingCollaborationApi meetingCollaborationApi(
            TenantContextPort tenantContextPort,
            ClockPort clockPort,
            MeetingMembershipGuard meetingMembershipGuard,
            MeetingMarkerRepository meetingMarkerRepository,
            SharedNoteRepository sharedNoteRepository,
            PrivateNoteRepository privateNoteRepository,
            MeetingAgendaRepository meetingAgendaRepository,
            OpenTaskRepository openTaskRepository,
            CollaborationIdempotencyStore collaborationIdempotencyStore
    ) {
        return new MeetingCollaborationService(
                tenantContextPort,
                clockPort,
                meetingMembershipGuard,
                meetingMarkerRepository,
                sharedNoteRepository,
                privateNoteRepository,
                meetingAgendaRepository,
                openTaskRepository,
                collaborationIdempotencyStore
        );
    }

    @Bean
    MeetingApplicationService meetingApplicationService(
            TenantContextPort tenantContextPort,
            BusinessContextRepository businessContextRepository,
            MeetingSeriesRepository meetingSeriesRepository,
            MeetingOccurrenceRepository meetingOccurrenceRepository,
            MeetingParticipantRepository meetingParticipantRepository,
            MeetingEventPublisher meetingEventPublisher,
            MeetingAuditPort meetingAuditPort,
            ClockPort clockPort
    ) {
        return new MeetingApplicationService(
                tenantContextPort,
                businessContextRepository,
                meetingSeriesRepository,
                meetingOccurrenceRepository,
                meetingParticipantRepository,
                meetingEventPublisher,
                meetingAuditPort,
                clockPort
        );
    }

    @Bean
    BusinessContextApplicationService businessContextApplicationService(
            TenantContextPort tenantContextPort,
            BusinessContextRepository businessContextRepository,
            MeetingAuditPort meetingAuditPort,
            ClockPort clockPort
    ) {
        return new BusinessContextApplicationService(
                tenantContextPort, businessContextRepository, meetingAuditPort, clockPort
        );
    }

    @Bean
    MeetingApi meetingApi(
            MeetingApplicationService meetingApplicationService,
            BusinessContextApplicationService businessContextApplicationService
    ) {
        return new MeetingApiFacade(meetingApplicationService, businessContextApplicationService);
    }
}
