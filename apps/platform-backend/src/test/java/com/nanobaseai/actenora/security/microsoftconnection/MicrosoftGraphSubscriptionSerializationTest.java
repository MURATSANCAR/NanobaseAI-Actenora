package com.nanobaseai.actenora.security.microsoftconnection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nanobaseai.actenora.microsoftconnection.application.model.GraphSubscription;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MicrosoftGraphSubscriptionSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void subscriptionViewSerializesExpirationAsIso8601() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Instant expiration = Instant.parse("2026-07-28T09:06:05Z");
        MicrosoftGraphSubscriptionController.SubscriptionView view =
                MicrosoftGraphSubscriptionController.SubscriptionView.from(
                        new GraphSubscription(
                                tenantId,
                                "sub-1",
                                "users/mailbox@contoso.com/events",
                                "created,updated,deleted",
                                "https://portal.example/api/v1/microsoft/webhooks/graph-notifications",
                                "client-state",
                                expiration,
                                "app-id"
                        )
                );

        String json = mapper.writeValueAsString(List.of(view));

        assertTrue(json.contains("\"expirationDateTime\":\"2026-07-28T09:06:05Z\""));
    }
}
