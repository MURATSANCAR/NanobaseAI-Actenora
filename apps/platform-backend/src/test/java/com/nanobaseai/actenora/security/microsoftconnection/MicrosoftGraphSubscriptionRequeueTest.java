package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.identity.api.Permission;
import com.nanobaseai.actenora.microsoftconnection.api.MicrosoftConnectionApi;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.config.MicrosoftGraphSpringProperties;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;
import com.nanobaseai.actenora.sharedkernel.security.AuthenticatedPrincipal;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class MicrosoftGraphSubscriptionRequeueTest {

    private TranscriptPollWorkStore workStore;
    private MicrosoftGraphSubscriptionController controller;
    private UUID tenantId;
    private UUID meetingId;

    @BeforeEach
    void setUp() {
        workStore = new InMemoryTranscriptPollWorkStore();
        controller = new MicrosoftGraphSubscriptionController(
                mock(MicrosoftConnectionApi.class),
                workStore,
                mock(GraphMailboxSyncService.class),
                new MicrosoftGraphSpringProperties(),
                true);
        tenantId = UUID.randomUUID();
        meetingId = UUID.randomUUID();
        TenantSecurityContext.set(new AuthenticatedPrincipal(
                TenantId.of(tenantId),
                UUID.randomUUID(),
                "entra-oid",
                "ops@contoso.com",
                "Ops",
                Set.of(),
                Set.of(Permission.OPERATIONS_MANAGE.code()),
                false
        ));
    }

    @AfterEach
    void tearDown() {
        TenantSecurityContext.clear();
    }

    @Test
    void requeuesDeadLetteredTranscriptPoll() {
        workStore.enqueue(tenantId, meetingId, java.time.Instant.parse("2026-07-26T08:00:00Z"));
        workStore.deadLetter(tenantId, meetingId, 3, "GRAPH_MAILBOX_CONFIGURATION_MISSING",
                java.time.Instant.parse("2026-07-26T08:05:00Z"));

        assertDoesNotThrow(() -> controller.requeueTranscriptPoll(meetingId));
        assertEquals(1, workStore.countPending());
    }

    @Test
    void rejectsRequeueWhenNotDeadLettered() {
        workStore.enqueue(tenantId, meetingId, java.time.Instant.parse("2026-07-26T08:00:00Z"));
        ActenoraException ex = assertThrows(
                ActenoraException.class,
                () -> controller.requeueTranscriptPoll(meetingId));
        assertEquals("TRANSCRIPT_POLL_NOT_REQUEUEABLE", ex.code());
    }
}
