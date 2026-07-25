package com.nanobaseai.actenora.meeting.infrastructure.web;

import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meeting.api.dto.CreateMeetingRequest;
import com.nanobaseai.actenora.meeting.api.dto.CursorPageRequest;
import com.nanobaseai.actenora.meeting.api.dto.MeetingListResponse;
import com.nanobaseai.actenora.meeting.api.dto.MeetingResponse;
import com.nanobaseai.actenora.meeting.api.dto.MeetingStatusTransitionRequest;
import com.nanobaseai.actenora.meeting.api.dto.ParticipantResponse;
import com.nanobaseai.actenora.meeting.api.dto.UpdateMeetingRequest;
import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrenceStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/meetings")
public class MeetingController {

    private final MeetingApi meetingApi;

    public MeetingController(MeetingApi meetingApi) {
        this.meetingApi = meetingApi;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MeetingResponse create(@RequestBody CreateMeetingRequest request) {
        return meetingApi.createMeeting(request);
    }

    @PutMapping("/{id}")
    public MeetingResponse update(@PathVariable("id") UUID id, @RequestBody UpdateMeetingRequest request) {
        return meetingApi.updateMeeting(id, request);
    }

    @GetMapping("/{id}")
    public MeetingResponse detail(@PathVariable("id") UUID id) {
        return meetingApi.getMeeting(id);
    }

    @GetMapping
    public MeetingListResponse list(
            @RequestParam(value = "status", required = false) MeetingOccurrenceStatus status,
            @RequestParam(value = "businessContextId", required = false) UUID businessContextId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return meetingApi.listMeetings(new CursorPageRequest(status, businessContextId, cursor, limit));
    }

    @PostMapping("/{id}/transitions")
    public MeetingResponse transition(
            @PathVariable("id") UUID id,
            @RequestBody MeetingStatusTransitionRequest request
    ) {
        return meetingApi.transitionMeetingStatus(id, request);
    }

    @GetMapping("/{id}/participants")
    public List<ParticipantResponse> participants(@PathVariable("id") UUID id) {
        return meetingApi.listParticipants(id);
    }
}
