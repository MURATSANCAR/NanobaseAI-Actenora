package com.nanobaseai.actenora.meeting.infrastructure.web;

import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meeting.api.dto.BusinessContextResponse;
import com.nanobaseai.actenora.meeting.api.dto.CreateBusinessContextRequest;
import com.nanobaseai.actenora.meeting.api.dto.UpdateBusinessContextRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/business-contexts")
public class BusinessContextController {

    private final MeetingApi meetingApi;

    public BusinessContextController(MeetingApi meetingApi) {
        this.meetingApi = meetingApi;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BusinessContextResponse create(@RequestBody CreateBusinessContextRequest request) {
        return meetingApi.createBusinessContext(request);
    }

    @GetMapping
    public List<BusinessContextResponse> list() {
        return meetingApi.listBusinessContexts();
    }

    @PutMapping("/{id}")
    public BusinessContextResponse update(
            @PathVariable("id") UUID id,
            @RequestBody UpdateBusinessContextRequest request
    ) {
        return meetingApi.updateBusinessContext(id, request);
    }
}
