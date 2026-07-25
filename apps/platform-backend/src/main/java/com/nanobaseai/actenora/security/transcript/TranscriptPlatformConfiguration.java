package com.nanobaseai.actenora.security.transcript;

import com.nanobaseai.actenora.meeting.application.port.MeetingOccurrenceRepository;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.transcript.application.port.out.KnownMeetingOccurrenceStore;
import com.nanobaseai.actenora.transcript.infrastructure.persistence.InMemoryKnownMeetingOccurrenceStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.UUID;

/**
 * FAZ 8 — bind transcript upload guard to meeting occurrences without cross-schema FK.
 */
@Configuration
public class TranscriptPlatformConfiguration {

    @Bean
    @Primary
    KnownMeetingOccurrenceStore meetingBackedKnownMeetingOccurrenceStore(
            MeetingOccurrenceRepository meetingOccurrenceRepository
    ) {
        InMemoryKnownMeetingOccurrenceStore remembered = new InMemoryKnownMeetingOccurrenceStore();
        return new KnownMeetingOccurrenceStore() {
            @Override
            public void remember(TenantId tenantId, UUID meetingOccurrenceId) {
                remembered.remember(tenantId, meetingOccurrenceId);
            }

            @Override
            public boolean isKnown(TenantId tenantId, UUID meetingOccurrenceId) {
                if (remembered.isKnown(tenantId, meetingOccurrenceId)) {
                    return true;
                }
                return meetingOccurrenceRepository
                        .findByIdAndTenantId(meetingOccurrenceId, tenantId)
                        .isPresent();
            }
        };
    }
}
