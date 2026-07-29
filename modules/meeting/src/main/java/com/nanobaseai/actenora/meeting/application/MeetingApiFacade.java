package com.nanobaseai.actenora.meeting.application;

import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meeting.api.dto.ApplyAttendanceRequest;
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
import com.nanobaseai.actenora.meeting.application.MeetingMapper;
import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrenceStatus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MeetingApiFacade implements MeetingApi {

    private final MeetingApplicationService meetingService;
    private final BusinessContextApplicationService businessContextService;

    public MeetingApiFacade(
            MeetingApplicationService meetingService,
            BusinessContextApplicationService businessContextService
    ) {
        this.meetingService = Objects.requireNonNull(meetingService);
        this.businessContextService = Objects.requireNonNull(businessContextService);
    }

    @Override
    public MeetingResponse createMeeting(CreateMeetingRequest request) {
        return meetingService.create(request);
    }

    @Override
    public MeetingResponse updateMeeting(UUID meetingId, UpdateMeetingRequest request) {
        return meetingService.update(meetingId, request);
    }

    @Override
    public MeetingResponse getMeeting(UUID meetingId) {
        return meetingService.detail(meetingId);
    }

    @Override
    public Optional<MeetingResponse> findByGraphEventImmutableId(String graphEventImmutableId) {
        return meetingService.findByGraphEventImmutableId(graphEventImmutableId);
    }

    @Override
    public MeetingListResponse listMeetings(CursorPageRequest pageRequest) {
        return meetingService.list(pageRequest);
    }

    @Override
    public List<MeetingResponse> searchMeetings(
            String query,
            MeetingOccurrenceStatus status,
            int limit
    ) {
        return meetingService.search(query, status, limit);
    }

    @Override
    public MeetingResponse transitionMeetingStatus(UUID meetingId, MeetingStatusTransitionRequest request) {
        return meetingService.transitionStatus(meetingId, request);
    }

    @Override
    public MeetingResponse advanceMeetingLifecycle(UUID meetingId, boolean cancelled) {
        return meetingService.advanceLifecycle(meetingId, cancelled);
    }

    @Override
    public List<MeetingResponse> listMeetingsDueForLifecycleAdvance(int limit) {
        return meetingService.findDueForLifecycleAdvance(limit).stream()
                .map(MeetingMapper::toResponse)
                .toList();
    }

    @Override
    public List<ParticipantResponse> listParticipants(UUID meetingId) {
        return meetingService.listParticipants(meetingId);
    }

    @Override
    public List<ParticipantResponse> applyAttendance(UUID meetingId, ApplyAttendanceRequest request) {
        return meetingService.applyAttendance(meetingId, request);
    }

    @Override
    public BusinessContextResponse createBusinessContext(CreateBusinessContextRequest request) {
        return businessContextService.create(request);
    }

    @Override
    public List<BusinessContextResponse> listBusinessContexts() {
        return businessContextService.list();
    }

    @Override
    public BusinessContextResponse updateBusinessContext(UUID id, UpdateBusinessContextRequest request) {
        return businessContextService.update(id, request);
    }
}
