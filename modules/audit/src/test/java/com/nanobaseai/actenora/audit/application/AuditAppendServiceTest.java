package com.nanobaseai.actenora.audit.application;

import com.nanobaseai.actenora.audit.domain.AuditEntry;
import com.nanobaseai.actenora.audit.infrastructure.InMemoryAuditEntryStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuditAppendServiceTest {

    @Test
    void appendOnlyTimelineIsOrderedByOccurredAt() {
        InMemoryAuditEntryStore store = new InMemoryAuditEntryStore();
        AuditAppendService service = new AuditAppendService(store);
        UUID tenantId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        service.append(tenantId, "a", "APPROVAL_REQUESTED", "ApprovalRequest", resourceId,
                Map.of("step", 1), Instant.parse("2026-07-25T10:00:00Z"));
        service.append(tenantId, "a", "APPROVAL_DECISION_APPROVE", "ApprovalRequest", resourceId,
                Map.of("comment", "ok"), Instant.parse("2026-07-25T11:00:00Z"));

        List<AuditEntry> timeline = service.timeline(tenantId, resourceId);
        assertEquals(2, timeline.size());
        assertEquals("APPROVAL_REQUESTED", timeline.get(0).action());
        assertEquals("APPROVAL_DECISION_APPROVE", timeline.get(1).action());
        assertEquals("ok", timeline.get(1).metadata().get("comment"));
    }

    @Test
    void appendStripsForbiddenTranscriptKeys() {
        InMemoryAuditEntryStore store = new InMemoryAuditEntryStore();
        AuditAppendService service = new AuditAppendService(store);
        UUID tenantId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        service.append(tenantId, "a", "NOTE_VIEWED", "MeetingNote", resourceId,
                Map.of("transcript", "should-not-persist", "noteId", "n1"),
                Instant.parse("2026-07-25T10:00:00Z"));

        Map<String, Object> metadata = service.timeline(tenantId, resourceId).getFirst().metadata();
        assertEquals(false, metadata.containsKey("transcript"));
        assertEquals("n1", metadata.get("noteId"));
    }
}
