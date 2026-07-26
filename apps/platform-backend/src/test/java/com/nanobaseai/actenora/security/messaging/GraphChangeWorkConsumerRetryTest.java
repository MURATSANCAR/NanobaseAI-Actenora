package com.nanobaseai.actenora.security.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobaseai.actenora.microsoftconnection.api.MicrosoftConnectionApi;
import com.nanobaseai.actenora.security.microsoftconnection.CalendarMeetingUpsertAdapter;
import com.nanobaseai.actenora.security.microsoftconnection.GraphChangeNotificationProcessor;
import com.nanobaseai.actenora.security.microsoftconnection.GraphChangeWorkConsumer;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.EventBackbone;
import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import com.nanobaseai.actenora.sharedkernel.messaging.EventMessagingConfig;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GraphChangeWorkConsumerRetryTest {

    @Test
    void transientFailureRetriesThenDeadLetters() {
        MicrosoftConnectionApi api = mock(MicrosoftConnectionApi.class);
        when(api.syncCalendar(any(), anyString())).thenThrow(new IllegalStateException("Graph unavailable"));
        GraphChangeWorkConsumer handler = new GraphChangeWorkConsumer(
                api,
                mock(CalendarMeetingUpsertAdapter.class),
                new ObjectMapper());
        EventBackbone backbone = EventBackbone.inMemory(
                EventMessagingConfig.defaults("test").withMaxAttempts(2));
        var consumer = backbone.consumer("microsoft-connection");
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(),
                GraphChangeNotificationProcessor.GRAPH_CHANGE_RECEIVED,
                1,
                Instant.now(),
                TenantId.random(),
                "GraphSubscription",
                "sub-1",
                null,
                null,
                null,
                "microsoft-connection",
                "{\"resource\":\"users/organizer@contoso.com/events\"}");

        assertEquals(
                com.nanobaseai.actenora.sharedkernel.messaging.inbox.IdempotentEventConsumer.Outcome.RETRY,
                EventBackboneConsumerDispatch.dispatchGraphChange(envelope, consumer, handler).outcome());
        assertEquals(
                com.nanobaseai.actenora.sharedkernel.messaging.inbox.IdempotentEventConsumer.Outcome.DEAD_LETTER,
                EventBackboneConsumerDispatch.dispatchGraphChange(envelope, consumer, handler).outcome());
        assertEquals(1, backbone.deadLetterStore().listOpen(10).size());
    }
}
