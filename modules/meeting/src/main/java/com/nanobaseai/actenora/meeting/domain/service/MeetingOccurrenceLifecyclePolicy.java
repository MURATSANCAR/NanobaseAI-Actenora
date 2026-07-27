package com.nanobaseai.actenora.meeting.domain.service;

import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrenceStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Time- and cancel-driven lifecycle hops that respect {@link MeetingOccurrenceStateMachine}.
 * Catch-up walks the happy path (e.g. DRAFT → SCHEDULED → IN_PROGRESS → ENDED).
 */
public final class MeetingOccurrenceLifecyclePolicy {

    private MeetingOccurrenceLifecyclePolicy() {
    }

    /**
     * Ordered status hops to apply from {@code current} given schedule and clock.
     * Empty when already at the correct terminal/current state.
     */
    public static List<MeetingOccurrenceStatus> nextHops(
            MeetingOccurrenceStatus current,
            Instant scheduledStartAt,
            Instant scheduledEndAt,
            Instant now,
            boolean cancelled
    ) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(now, "now");
        if (current == MeetingOccurrenceStatus.ENDED || current == MeetingOccurrenceStatus.CANCELLED) {
            return List.of();
        }
        if (cancelled) {
            if (MeetingOccurrenceStateMachine.canTransition(current, MeetingOccurrenceStatus.CANCELLED)) {
                return List.of(MeetingOccurrenceStatus.CANCELLED);
            }
            return List.of();
        }
        Objects.requireNonNull(scheduledStartAt, "scheduledStartAt");
        Objects.requireNonNull(scheduledEndAt, "scheduledEndAt");

        MeetingOccurrenceStatus target = targetStatus(scheduledStartAt, scheduledEndAt, now);
        List<MeetingOccurrenceStatus> hops = new ArrayList<>(3);
        MeetingOccurrenceStatus cursor = current;
        while (cursor != target) {
            MeetingOccurrenceStatus next = nextAlongHappyPath(cursor, target);
            if (next == null || !MeetingOccurrenceStateMachine.canTransition(cursor, next)) {
                break;
            }
            hops.add(next);
            cursor = next;
        }
        return List.copyOf(hops);
    }

    static MeetingOccurrenceStatus targetStatus(Instant scheduledStartAt, Instant scheduledEndAt, Instant now) {
        if (!now.isBefore(scheduledEndAt)) {
            return MeetingOccurrenceStatus.ENDED;
        }
        if (!now.isBefore(scheduledStartAt)) {
            return MeetingOccurrenceStatus.IN_PROGRESS;
        }
        return MeetingOccurrenceStatus.SCHEDULED;
    }

    private static MeetingOccurrenceStatus nextAlongHappyPath(
            MeetingOccurrenceStatus cursor,
            MeetingOccurrenceStatus target
    ) {
        return switch (cursor) {
            case DRAFT -> MeetingOccurrenceStatus.SCHEDULED;
            case SCHEDULED -> target == MeetingOccurrenceStatus.SCHEDULED
                    ? null
                    : MeetingOccurrenceStatus.IN_PROGRESS;
            case IN_PROGRESS -> target == MeetingOccurrenceStatus.ENDED
                    ? MeetingOccurrenceStatus.ENDED
                    : null;
            case ENDED, CANCELLED -> null;
        };
    }
}
