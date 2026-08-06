package com.nanobaseai.actenora.security.meeting;

import com.nanobaseai.actenora.meeting.domain.model.MeetingParticipant;
import com.nanobaseai.actenora.meeting.domain.model.ParticipantType;
import com.nanobaseai.actenora.meeting.infrastructure.persistence.InMemoryMeetingOccurrenceRepository;
import com.nanobaseai.actenora.meeting.infrastructure.persistence.InMemoryMeetingParticipantRepository;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MeetingOccurrenceClockAdapterTest {

    @Test
    void ownerRosterContainsOnlyActualAttendeesAndCanonicalizesAliases() {
        TenantId tenantId = TenantId.random();
        UUID meetingId = UUID.randomUUID();
        InMemoryMeetingParticipantRepository participants = new InMemoryMeetingParticipantRepository();

        MeetingParticipant joined = MeetingParticipant.create(
                tenantId, meetingId, null, "Ali  BAĞATIR (GMY)", null,
                ParticipantType.REQUIRED, true);
        joined.markJoined(null, null);
        participants.save(joined);
        participants.save(MeetingParticipant.create(
                tenantId, meetingId, "oid-absent", "Gelmedi Davetli", "absent@example.com",
                ParticipantType.REQUIRED, false));

        MeetingOccurrenceClockAdapter adapter = new MeetingOccurrenceClockAdapter(
                new InMemoryMeetingOccurrenceRepository(), participants);

        assertEquals(List.of("Ali BAĞATIR"), adapter.participantDisplayNames(tenantId, meetingId));
    }
}
