package com.nanobaseai.actenora.meeting.infrastructure;

import com.nanobaseai.actenora.meeting.domain.MeetingEntity;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port implementation boundary for meetings.
 * Must not be injected outside the meeting module.
 */
public interface MeetingRepository {

    Optional<MeetingEntity> findById(UUID id);

    MeetingEntity save(MeetingEntity meeting);
}
