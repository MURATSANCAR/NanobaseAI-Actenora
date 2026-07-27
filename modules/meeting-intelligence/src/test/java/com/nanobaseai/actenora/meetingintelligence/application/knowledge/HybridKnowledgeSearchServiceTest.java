package com.nanobaseai.actenora.meetingintelligence.application.knowledge;

import com.nanobaseai.actenora.meetingintelligence.application.port.EmbeddingPort;
import com.nanobaseai.actenora.meetingintelligence.domain.knowledge.KnowledgeItemKind;
import com.nanobaseai.actenora.meetingintelligence.domain.knowledge.KnowledgeSearchHit;
import com.nanobaseai.actenora.meetingintelligence.domain.knowledge.MeetingKnowledgeItem;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.embedding.HashEmbeddingPort;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryMeetingKnowledgeStore;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridKnowledgeSearchServiceTest {

    @Test
    void fusesFtsAndVectorHits() {
        InMemoryMeetingKnowledgeStore store = new InMemoryMeetingKnowledgeStore();
        EmbeddingPort embeddings = new HashEmbeddingPort(32);
        TenantId tenant = TenantId.of(UUID.randomUUID());
        UUID occurrence = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-27T10:00:00Z");

        store.upsert(MeetingKnowledgeItem.create(
                tenant, occurrence, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                KnowledgeItemKind.DECISION, "Approve budget increase for Q3",
                embeddings.embed("Approve budget increase for Q3"), now
        ));
        store.upsert(MeetingKnowledgeItem.create(
                tenant, occurrence, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                KnowledgeItemKind.ACTION_ITEM, "Schedule follow-up with finance",
                embeddings.embed("Schedule follow-up with finance"), now
        ));

        HybridKnowledgeSearchService search = new HybridKnowledgeSearchService(store, embeddings);
        List<KnowledgeSearchHit> hits = search.search(tenant, "budget", 5);

        assertFalse(hits.isEmpty());
        assertTrue(hits.stream().anyMatch(h -> h.content().toLowerCase().contains("budget")));
        assertEquals(KnowledgeItemKind.DECISION, hits.getFirst().itemKind());
    }
}
