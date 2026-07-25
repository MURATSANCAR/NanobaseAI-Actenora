package com.nanobaseai.actenora.meeting.application.collaboration;

import com.nanobaseai.actenora.meeting.domain.collaboration.PrivateNote;
import com.nanobaseai.actenora.meeting.infrastructure.collaboration.InMemoryPrivateNoteRepository;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivateNoteRetentionServiceTest {

    @Test
    void deletesExpiredPrivateNotes() {
        InMemoryPrivateNoteRepository repo = new InMemoryPrivateNoteRepository();
        PrivateNoteRetentionService service = new PrivateNoteRetentionService(repo);
        TenantId tenant = TenantId.random();
        Instant created = Instant.parse("2025-01-01T00:00:00Z");
        Instant now = Instant.parse("2026-07-25T00:00:00Z");

        PrivateNote note = PrivateNote.create(tenant, UUID.randomUUID(), UUID.randomUUID(), "secret", created);
        repo.save(note);

        assertEquals(1, service.findExpired(tenant, 365, now).size());
        service.deleteForRetention(tenant, note.id());
        assertTrue(repo.findByIdAndTenantId(note.id(), tenant).isEmpty());
    }
}
