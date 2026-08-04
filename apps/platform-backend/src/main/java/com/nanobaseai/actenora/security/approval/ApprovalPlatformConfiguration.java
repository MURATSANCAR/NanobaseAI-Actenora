package com.nanobaseai.actenora.security.approval;

import com.nanobaseai.actenora.approval.api.ApprovalApi;
import com.nanobaseai.actenora.approval.application.ApprovalWorkflowService;
import com.nanobaseai.actenora.approval.application.port.ApprovalAuditPort;
import com.nanobaseai.actenora.approval.application.port.ApprovalRequestRepository;
import com.nanobaseai.actenora.approval.application.port.ParticipantDisputeRepository;
import com.nanobaseai.actenora.approval.infrastructure.ApprovalApiAdapter;
import com.nanobaseai.actenora.approval.infrastructure.InMemoryApprovalRequestRepository;
import com.nanobaseai.actenora.approval.infrastructure.InMemoryParticipantDisputeRepository;
import com.nanobaseai.actenora.audit.api.AuditApi;
import com.nanobaseai.actenora.meetingintelligence.application.MeetingNoteApprovalService;
import com.nanobaseai.actenora.meetingintelligence.application.port.ApprovedNoteLedgerPort;
import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingIntelligenceAuditPort;
import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingNoteRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingNoteVersionRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.NoteApprovalOpenedNotifier;
import com.nanobaseai.actenora.meetingintelligence.application.port.NoteArtifactStoragePort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * FAZ 18 — Approval (InMemory) + MeetingNoteApprovalService wiring.
 * FAZ 27 — Approved note artifacts → continuity ledger handoff.
 */
@Configuration
public class ApprovalPlatformConfiguration {

    @ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "inmemory", matchIfMissing = true)
    @ConditionalOnMissingBean(ApprovalRequestRepository.class)
    @Bean
    public ApprovalRequestRepository inMemoryApprovalRequestRepository() {
        return new InMemoryApprovalRequestRepository();
    }

    @ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "inmemory", matchIfMissing = true)
    @ConditionalOnMissingBean(ParticipantDisputeRepository.class)
    @Bean
    public ParticipantDisputeRepository inMemoryParticipantDisputeRepository() {
        return new InMemoryParticipantDisputeRepository();
    }

    @Bean
    @Primary
    public ApprovalAuditPort auditBackedApprovalAuditPort(AuditApi auditApi) {
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
    public ApprovalWorkflowService approvalWorkflowService(
            ApprovalRequestRepository requests,
            ParticipantDisputeRepository disputes,
            ApprovalAuditPort auditPort
    ) {
        return new ApprovalWorkflowService(requests, disputes, auditPort, Clock.systemUTC());
    }

    @Bean
    public ApprovalApi approvalApi(ApprovalWorkflowService workflow) {
        return new ApprovalApiAdapter(workflow);
    }

    @Bean
    public MeetingNoteApprovalService meetingNoteApprovalService(
            MeetingNoteRepository notes,
            MeetingNoteVersionRepository versions,
            ApprovalApi approvalApi,
            MeetingIntelligenceAuditPort auditPort,
            ApprovedNoteLedgerPort approvedNoteLedgerPort,
            NoteArtifactStoragePort noteArtifactStorage,
            ObjectProvider<NoteApprovalOpenedNotifier> approvalOpenedNotifier,
            ObjectProvider<com.nanobaseai.actenora.meetingintelligence.application.port.ApprovedNoteFinalDeliveryPort> finalDelivery
    ) {
        NoteApprovalOpenedNotifier deferred = (tenantId, noteId, meetingOccurrenceId, approvalId, approverId) -> {
            NoteApprovalOpenedNotifier notifier = approvalOpenedNotifier.getIfAvailable();
            if (notifier != null) {
                notifier.onSubmitted(tenantId, noteId, meetingOccurrenceId, approvalId, approverId);
            }
        };
        return new MeetingNoteApprovalService(
                notes,
                versions,
                approvalApi,
                auditPort,
                approvedNoteLedgerPort,
                noteArtifactStorage,
                deferred,
                () -> {
                    var delivery = finalDelivery.getIfAvailable();
                    return delivery == null
                            ? com.nanobaseai.actenora.meetingintelligence.application.port.ApprovedNoteFinalDeliveryPort.noop()
                            : delivery;
                },
                Clock.systemUTC()
        );
    }
}
