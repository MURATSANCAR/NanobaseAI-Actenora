package com.nanobaseai.actenora.meeting.api;

import com.nanobaseai.actenora.meeting.api.dto.BusinessContextResponse;
import com.nanobaseai.actenora.meeting.api.dto.CreateBusinessContextRequest;
import com.nanobaseai.actenora.meeting.api.dto.CreateMeetingRequest;
import com.nanobaseai.actenora.meeting.api.dto.CursorPageRequest;
import com.nanobaseai.actenora.meeting.api.dto.MeetingListResponse;
import com.nanobaseai.actenora.meeting.api.dto.MeetingResponse;
import com.nanobaseai.actenora.meeting.api.dto.MeetingStatusTransitionRequest;
import com.nanobaseai.actenora.meeting.api.dto.ParticipantResponse;
import com.nanobaseai.actenora.meeting.api.dto.UpdateBusinessContextRequest;
import com.nanobaseai.actenora.meeting.api.dto.UpdateMeetingRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public façade for the Meeting bounded context.
 * Cross-module callers use types in this package only.
 *
 * <p>Series/relations continuity (FAZ 7) is exposed via
 * {@link com.nanobaseai.actenora.meeting.api.relation.MeetingRelationApi}.
 *
 * <p>In-meeting Teams collaboration (FAZ 22) is exposed via
 * {@link com.nanobaseai.actenora.meeting.api.collaboration.MeetingCollaborationApi}.
 */
public interface MeetingApi {

    MeetingResponse createMeeting(CreateMeetingRequest request);

    MeetingResponse updateMeeting(UUID meetingId, UpdateMeetingRequest request);

    MeetingResponse getMeeting(UUID meetingId);

    Optional<MeetingResponse> findByGraphEventImmutableId(String graphEventImmutableId);

    MeetingListResponse listMeetings(CursorPageRequest pageRequest);

    MeetingResponse transitionMeetingStatus(UUID meetingId, MeetingStatusTransitionRequest request);

    List<ParticipantResponse> listParticipants(UUID meetingId);

    BusinessContextResponse createBusinessContext(CreateBusinessContextRequest request);

    List<BusinessContextResponse> listBusinessContexts();

    BusinessContextResponse updateBusinessContext(UUID id, UpdateBusinessContextRequest request);
}
