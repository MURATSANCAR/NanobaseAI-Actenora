package com.nanobaseai.actenora.meetingintelligence.application.knowledge;

import com.nanobaseai.actenora.meetingintelligence.application.port.EmbeddingPort;
import com.nanobaseai.actenora.meetingintelligence.application.port.HybridKnowledgeSearchPort;
import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingKnowledgeStorePort;
import com.nanobaseai.actenora.meetingintelligence.domain.knowledge.KnowledgeSearchHit;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Reciprocal Rank Fusion over FTS and vector result lists (k=60).
 */
public final class HybridKnowledgeSearchService implements HybridKnowledgeSearchPort {

    private static final int RRF_K = 60;

    private final MeetingKnowledgeStorePort store;
    private final EmbeddingPort embeddings;

    public HybridKnowledgeSearchService(MeetingKnowledgeStorePort store, EmbeddingPort embeddings) {
        this.store = Objects.requireNonNull(store, "store");
        this.embeddings = Objects.requireNonNull(embeddings, "embeddings");
    }

    @Override
    public List<KnowledgeSearchHit> search(TenantId tenantId, String query, int limit) {
        Objects.requireNonNull(tenantId, "tenantId");
        if (query == null || query.isBlank() || limit < 1) {
            return List.of();
        }
        int fetch = Math.max(limit * 3, limit);
        List<KnowledgeSearchHit> fts = store.searchFts(tenantId, query.trim(), fetch);
        List<KnowledgeSearchHit> vector = List.of();
        try {
            vector = store.searchVector(tenantId, embeddings.embed(query.trim()), fetch);
        } catch (RuntimeException ignored) {
            // lexical-only
        }

        Map<UUID, Double> scores = new HashMap<>();
        Map<UUID, KnowledgeSearchHit> docs = new HashMap<>();
        accumulate(fts, scores, docs);
        accumulate(vector, scores, docs);

        List<KnowledgeSearchHit> fused = new ArrayList<>();
        for (Map.Entry<UUID, Double> entry : scores.entrySet()) {
            KnowledgeSearchHit base = docs.get(entry.getKey());
            fused.add(new KnowledgeSearchHit(
                    base.itemId(),
                    base.tenantId(),
                    base.meetingOccurrenceId(),
                    base.sourceItemId(),
                    base.itemKind(),
                    base.content(),
                    entry.getValue()
            ));
        }
        fused.sort(Comparator.comparingDouble(KnowledgeSearchHit::score).reversed());
        if (fused.size() > limit) {
            return List.copyOf(fused.subList(0, limit));
        }
        return List.copyOf(fused);
    }

    private static void accumulate(
            List<KnowledgeSearchHit> hits,
            Map<UUID, Double> scores,
            Map<UUID, KnowledgeSearchHit> docs
    ) {
        for (int rank = 0; rank < hits.size(); rank++) {
            KnowledgeSearchHit hit = hits.get(rank);
            docs.putIfAbsent(hit.itemId(), hit);
            scores.merge(hit.itemId(), 1.0 / (RRF_K + rank + 1), Double::sum);
        }
    }
}
