package com.nanobaseai.actenora.meeting.application.collaboration;

import com.nanobaseai.actenora.meeting.application.port.MeetingOccurrenceRepository;
import com.nanobaseai.actenora.meeting.application.port.MeetingParticipantRepository;
import com.nanobaseai.actenora.meeting.domain.collaboration.UnauthorizedMeetingAccessException;
import com.nanobaseai.actenora.meeting.domain.exception.MeetingNotFoundException;
import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrence;
import com.nanobaseai.actenora.meeting.domain.model.MeetingParticipant;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Meeting membership gate for Teams collaboration surfaces.
 */
public final class MeetingMembershipGuard {

    private final MeetingOccurrenceRepository occurrenceRepository;
    private final MeetingParticipantRepository participantRepository;

    public MeetingMembershipGuard(
            MeetingOccurrenceRepository occurrenceRepository,
            MeetingParticipantRepository participantRepository
    ) {
        this.occurrenceRepository = Objects.requireNonNull(occurrenceRepository);
        this.participantRepository = Objects.requireNonNull(participantRepository);
    }

    public MeetingOccurrence requireMemberMeeting(TenantId tenantId, UUID meetingOccurrenceId, UUID actorUserId) {
        MeetingOccurrence occurrence = occurrenceRepository.findByIdAndTenantId(meetingOccurrenceId, tenantId)
                .orElseThrow(() -> new MeetingNotFoundException(meetingOccurrenceId));
        if (!isMember(occurrence, actorUserId)) {
            throw new UnauthorizedMeetingAccessException(meetingOccurrenceId);
        }
        return occurrence;
    }

    public boolean isMember(MeetingOccurrence occurrence, UUID actorUserId) {
        Objects.requireNonNull(occurrence, "occurrence");
        Objects.requireNonNull(actorUserId, "actorUserId");
        if (occurrence.organizerUserId().equals(actorUserId)) {
            return true;
        }
        List<MeetingParticipant> participants = participantRepository.findByMeetingOccurrenceIdAndTenantId(
                occurrence.id(), occurrence.tenantId()
        );
        String actorAsString = actorUserId.toString();
        for (MeetingParticipant participant : participants) {
            if (actorAsString.equals(participant.entraUserId())) {
                return true;
            }
        }
        return false;
    }
}
