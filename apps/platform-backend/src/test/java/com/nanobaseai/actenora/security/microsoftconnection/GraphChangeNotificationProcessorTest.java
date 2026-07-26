package com.nanobaseai.actenora.security.microsoftconnection;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphChangeNotificationProcessorTest {

    @Test
    void parseMailboxUserIdFromEventsResource() {
        Optional<String> userId = GraphChangeNotificationProcessor.parseMailboxUserId("users/alice@contoso.com/events");
        assertTrue(userId.isPresent());
        assertEquals("alice@contoso.com", userId.get());
    }

    @Test
    void parseMailboxUserIdIgnoresTranscriptResource() {
        assertTrue(GraphChangeNotificationProcessor.parseMailboxUserId(
                "communications/onlineMeetings('abc')/transcripts").isEmpty());
    }
}
