package com.nanobaseai.actenora.audit.application;

import com.nanobaseai.actenora.audit.domain.AuditEntry;
import com.nanobaseai.actenora.audit.domain.AuditRetentionPolicy;
import com.nanobaseai.actenora.audit.infrastructure.InMemoryAuditEntryStore;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditRetentionServiceTest {

    @Test
    void listsOnlyEntriesPastRetentionCutoff() {
        Instant now = Instant.parse("2026-07-25T12:00:00Z");
        InMemoryAuditEntryStore store = new InMemoryAuditEntryStore();
        UUID tenant = UUID.randomUUID();
        UUID resource = UUID.randomUUID();

        AuditEntry old = AuditEntry.append(
                tenant,
                "system",
                "LOGIN",
                "session",
                resource,
                Map.of(),
                now.minus(3000, ChronoUnit.DAYS));
        AuditEntry recent = AuditEntry.append(
                tenant,
                "system",
                "LOGIN",
                "session",
                resource,
                Map.of(),
                now.minus(10, ChronoUnit.DAYS));
        store.append(old);
        store.append(recent);

        AuditRetentionService service = new AuditRetentionService(
                store,
                AuditRetentionPolicy.systemDefaults(),
                new InstantClock(Clock.fixed(now, ZoneOffset.UTC)));

        var eligible = service.listEligibleForArchive(tenant);
        assertEquals(1, eligible.size());
        assertEquals(old.id(), eligible.getFirst().id());
        assertTrue(service.policy().isEligibleForArchive(old.occurredAt(), now));
    }
}
