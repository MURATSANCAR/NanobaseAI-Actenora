package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meeting.api.dto.ApplyAttendanceRequest;
import com.nanobaseai.actenora.meeting.api.dto.MeetingResponse;
import com.nanobaseai.actenora.microsoftconnection.api.MicrosoftConnectionApi;
import com.nanobaseai.actenora.microsoftconnection.application.model.DirectoryUser;
import com.nanobaseai.actenora.microsoftconnection.application.model.ParticipantMetadata;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
            log.warn("Attendance report empty meetingId={} teamsMeetingId={}", meeting.id(), teamsMeetingId);
            return 0;
        }

        Map<String, Optional<DirectoryUser>> directoryCache = new HashMap<>();
        List<ApplyAttendanceRequest.AttendanceRecord> attended = new ArrayList<>();
        int reliableIdentities = 0;

        for (ParticipantMetadata record : records) {
            if (!record.attended()) {
                continue;
            }
            String oid = looksLikeGuid(record.id()) ? record.id() : null;
            String email = record.emailOptional().orElse(record.upnOptional().orElse(null));
            if (!StringUtils.hasText(email) && oid != null) {
                email = resolveEmail(tenantId, oid, directoryCache);
            }
            if (StringUtils.hasText(email) || oid != null) {
                reliableIdentities++;
            }
            attended.add(new ApplyAttendanceRequest.AttendanceRecord(
                    email,
                    oid,
                    record.displayName(),
                    record.role(),
                    record.joinedAt(),
                    record.leftAt()
            ));
        }

        if (attended.isEmpty()) {
            log.warn("Attendance report had no joined rows meetingId={}", meeting.id());
            return 0;
        }

        // Only mark unmatched invitees ABSENT when at least one row carries email/OID
        // (name-only rows are too weak to trust a mass-absent sweep).
        boolean markMissingAsAbsent = reliableIdentities > 0;
        meetingApi.applyAttendance(meeting.id(), new ApplyAttendanceRequest(attended, markMissingAsAbsent));
        log.info(
                "Attendance synced meetingId={} attendedCount={} markMissingAsAbsent={}",
                meeting.id(),
                attended.size(),
                markMissingAsAbsent
        );
        return attended.size();
    }

    private String resolveEmail(
            TenantId tenantId,
            String objectId,
            Map<String, Optional<DirectoryUser>> cache
    ) {
        Optional<DirectoryUser> resolved = cache.computeIfAbsent(
                objectId.toLowerCase(),
                key -> microsoftConnectionApi.resolveDirectoryUser(tenantId.value(), objectId)
        );
        return resolved.flatMap(DirectoryUser::preferredEmail).orElse(null);
    }

    private static boolean looksLikeGuid(String value) {
        if (value == null) {
            return false;
        }
        return value.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }
}
