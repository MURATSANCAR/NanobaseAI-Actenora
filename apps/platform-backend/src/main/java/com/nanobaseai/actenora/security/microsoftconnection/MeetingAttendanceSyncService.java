package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meeting.api.dto.ApplyAttendanceRequest;
import com.nanobaseai.actenora.meeting.api.dto.MeetingResponse;
import com.nanobaseai.actenora.microsoftconnection.api.MicrosoftConnectionApi;
import com.nanobaseai.actenora.microsoftconnection.application.model.ParticipantMetadata;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Pulls Teams attendance reports and writes JOINED / ABSENT onto meeting participants.
 */
public final class MeetingAttendanceSyncService {

    private static final Logger log = LoggerFactory.getLogger(MeetingAttendanceSyncService.class);

    private final MicrosoftConnectionApi microsoftConnectionApi;
    private final MeetingApi meetingApi;

    public MeetingAttendanceSyncService(
            MicrosoftConnectionApi microsoftConnectionApi,
            MeetingApi meetingApi
    ) {
        this.microsoftConnectionApi = Objects.requireNonNull(microsoftConnectionApi);
        this.meetingApi = Objects.requireNonNull(meetingApi);
    }

    public int syncAttendance(
            TenantId tenantId,
            MeetingResponse meeting,
            String graphUserId,
            String teamsMeetingId
    ) {
        if (!StringUtils.hasText(graphUserId) || !StringUtils.hasText(teamsMeetingId)) {
            return 0;
        }
        List<ParticipantMetadata> records;
        try {
            records = microsoftConnectionApi.listParticipants(
                    tenantId.value(), graphUserId, teamsMeetingId);
        } catch (RuntimeException ex) {
            log.debug("Attendance report unavailable meetingId={}: {}", meeting.id(), ex.getMessage());
            return 0;
        }
        if (records.isEmpty()) {
            return 0;
        }
        // Presence on the Teams attendance report means the person joined.
        List<ApplyAttendanceRequest.AttendanceRecord> attended = new ArrayList<>(records.size());
        for (ParticipantMetadata record : records) {
            attended.add(new ApplyAttendanceRequest.AttendanceRecord(
                    record.emailOptional().orElse(null),
                    looksLikeGuid(record.id()) ? record.id() : null,
                    record.displayName(),
                    record.role(),
                    record.joinedAt(),
                    record.leftAt()
            ));
        }
        meetingApi.applyAttendance(meeting.id(), new ApplyAttendanceRequest(attended, true));
        log.info("Attendance synced meetingId={} attendedCount={}", meeting.id(), attended.size());
        return attended.size();
    }

    private static boolean looksLikeGuid(String value) {
        if (value == null) {
            return false;
        }
        return value.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }
}
