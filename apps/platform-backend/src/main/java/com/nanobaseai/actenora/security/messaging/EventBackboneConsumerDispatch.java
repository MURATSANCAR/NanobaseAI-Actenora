package com.nanobaseai.actenora.security.messaging;

import com.nanobaseai.actenora.meeting.api.event.MeetingIntegrationEvents;
import com.nanobaseai.actenora.meetingintelligence.api.event.MeetingIntelligenceIntegrationEvents;
import com.nanobaseai.actenora.security.meetingintelligence.NoteApprovedForLedgerHandler;
import com.nanobaseai.actenora.security.microsoftconnection.TeamsTranscriptPollScheduler;
import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import com.nanobaseai.actenora.sharedkernel.messaging.inbox.IdempotentEventConsumer;
import com.nanobaseai.actenora.transcript.api.contract.MeetingOccurrenceContracts;
import com.nanobaseai.actenora.transcript.api.event.TranscriptIntegrationEvents;
import com.nanobaseai.actenora.transcript.infrastructure.messaging.MeetingOccurrenceUpsertedHandler;

/**
 * Shared dispatch helpers for platform event consumers (InMemory fan-out and Rabbit listeners).
 */
final class EventBackboneConsumerDispatch {

    private EventBackboneConsumerDispatch() {
    }

    static void dispatchOccurrenceUpserted(
            EventEnvelope envelope,
            IdempotentEventConsumer consumer,
            MeetingOccurrenceUpsertedHandler handler,
            TeamsTranscriptPollScheduler transcriptPollScheduler) {
        if (!MeetingOccurrenceContracts.MEETING_OCCURRENCE_UPSERTED.equals(envelope.eventType())
                && !MeetingIntegrationEvents.MEETING_OCCURRENCE_UPSERTED.equals(envelope.eventType())) {
            return;
        }
        consumer.consume(envelope, e -> {
            handler.handle(e);
            if (transcriptPollScheduler != null) {
                transcriptPollScheduler.onMeetingOccurrenceUpserted(e);
            }
        });
    }

    static void dispatchOccurrenceUpserted(
            EventEnvelope envelope,
            IdempotentEventConsumer consumer,
            MeetingOccurrenceUpsertedHandler handler) {
        dispatchOccurrenceUpserted(envelope, consumer, handler, null);
    }

    static void dispatchTranscriptReady(
            EventEnvelope envelope,
            IdempotentEventConsumer consumer,
            TranscriptReadyAiAdmissionHandler handler) {
        if (!TranscriptIntegrationEvents.TRANSCRIPT_READY.equals(envelope.eventType())) {
            return;
        }
        consumer.consume(envelope, handler::handle);
    }

    static void dispatchNoteApprovedForLedger(
            EventEnvelope envelope,
            IdempotentEventConsumer consumer,
            NoteApprovedForLedgerHandler handler) {
        if (!MeetingIntelligenceIntegrationEvents.NOTE_APPROVED_FOR_LEDGER.equals(envelope.eventType())) {
            return;
        }
        consumer.consume(envelope, handler::handle);
    }
}
