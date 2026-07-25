package com.nanobaseai.actenora.meeting.application.port;

import com.nanobaseai.actenora.sharedkernel.domain.DomainEvent;

import java.util.List;

/**
 * Publishes domain events via transactional outbox (FAZ 10).
 */
public interface MeetingEventPublisher {

    void publishAll(List<? extends DomainEvent> events);
}
