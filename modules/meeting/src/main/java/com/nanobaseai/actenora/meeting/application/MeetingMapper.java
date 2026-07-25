package com.nanobaseai.actenora.meeting.application;

import com.nanobaseai.actenora.meeting.api.dto.BusinessContextResponse;
import com.nanobaseai.actenora.meeting.api.dto.MeetingResponse;
import com.nanobaseai.actenora.meeting.api.dto.ParticipantResponse;
import com.nanobaseai.actenora.meeting.domain.model.BusinessContext;
import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrence;
import com.nanobaseai.actenora.meeting.domain.model.MeetingParticipant;

final class MeetingMapper {

    private MeetingMapper() {
    }

    static MeetingResponse toResponse(MeetingOccurrence occurrence) {
        return new MeetingResponse(
                occurrence.id(),
                occurrence.tenantId().value(),
                occurrence.meetingSeriesId(),
                occurrence.businessContextId(),
                occurrence.graphEventImmutableId(),
                occurrence.icalUid(),
                occurrence.originalStartAt(),
                occurrence.teamsMeetingId(),
                occurrence.chatId(),
                occurrence.joinWebUrl(),
                occurrence.title(),
                occurrence.organizerUserId(),
                occurrence.scheduledStartAt(),
                occurrence.scheduledEndAt(),
                occurrence.actualStartAt(),
                occurrence.actualEndAt(),
                occurrence.status(),
                occurrence.processingPriority(),
                occurrence.createdAt(),
                occurrence.updatedAt(),
                occurrence.version()
        );
    }

    static BusinessContextResponse toResponse(BusinessContext context) {
        return new BusinessContextResponse(
                context.id(),
                context.tenantId().value(),
                context.type(),
                context.referenceCode(),
                context.name(),
                context.description(),
                context.status(),
                context.createdAt(),
                context.updatedAt(),
                context.version()
        );
    }

    static ParticipantResponse toResponse(MeetingParticipant participant) {
        return new ParticipantResponse(
                participant.id(),
                participant.meetingOccurrenceId(),
                participant.entraUserId(),
                participant.displayName(),
                participant.email(),
                participant.participantType(),
                participant.attendanceStatus(),
                participant.joinedAt(),
                participant.leftAt(),
                participant.isExternal()
        );
    }
}
