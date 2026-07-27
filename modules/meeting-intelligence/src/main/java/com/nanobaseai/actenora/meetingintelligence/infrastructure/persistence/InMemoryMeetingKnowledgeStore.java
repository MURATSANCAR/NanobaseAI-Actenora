package com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence;

import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingKnowledgeStorePort;
import com.nanobaseai.actenora.meetingintelligence.domain.knowledge.KnowledgeItemKind;
import com.nanobaseai.actenora.meetingintelligence.domain.knowledge.KnowledgeSearchHit;
import com.nanobaseai.actenora.meetingintelligence.domain.knowledge.MeetingKnowledgeItem;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryMeetingKnowledgeStore implements MeetingKnowledgeStorePort {

    private final Map<String, MeetingKnowledgeItem> byKey = new ConcurrentHashMap<>();

    @Override
    public void upsert(MeetingKnowledgeItem item) {
        Objects.requireNonNull(item, "item");
        byKey.put(key(item.tenantId(), item.sourceItemId(), item.itemKind()), item);
    }

    @Override
    public List<KnowledgeSearchHit> searchFts(TenantId tenantId, String query, int limit) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty() || limit < 1) {
            return List.of();
        }
        return byKey.values().stream()
                .filter(item -> item.tenantId().equals(tenantId))
                .filter(item -> item.content().toLowerCase(Locale.ROOT).contains(q))
                .sorted(Comparator.comparing(MeetingKnowledgeItem::createdAt).reversed())
                .limit(limit)
                .map(item -> toHit(item, 1.0))
                .toList();
    }

    @Override
    public List<KnowledgeSearchHit> searchVector(TenantId tenantId, float[] embedding, int limit) {
        Objects.requireNonNull(embedding, "embedding");
        if (limit < 1) {
            return List.of();
        }
        List<KnowledgeSearchHit> hits = new ArrayList<>();
        for (MeetingKnowledgeItem item : byKey.values()) {
            if (!item.tenantId().equals(tenantId) || item.embedding().isEmpty()) {
                continue;
            }
            double score = cosine(embedding, item.embedding().orElseThrow());
            hits.add(toHit(item, score));
        }
        hits.sort(Comparator.comparingDouble(KnowledgeSearchHit::score).reversed());
        if (hits.size() > limit) {
            return List.copyOf(hits.subList(0, limit));
        }
        return List.copyOf(hits);
    }

    @Override
    public List<MeetingKnowledgeItem> findByOccurrence(TenantId tenantId, UUID meetingOccurrenceId) {
        return byKey.values().stream()
                .filter(item -> item.tenantId().equals(tenantId))
                .filter(item -> item.meetingOccurrenceId().equals(meetingOccurrenceId))
                .toList();
    }

    @Override
    public List<MeetingKnowledgeItem> findBySource(
            TenantId tenantId,
            UUID sourceItemId,
            KnowledgeItemKind kind
    ) {
        return Optional.ofNullable(byKey.get(key(tenantId, sourceItemId, kind)))
                .map(List::of)
                .orElse(List.of());
    }

    private static KnowledgeSearchHit toHit(MeetingKnowledgeItem item, double score) {
        return new KnowledgeSearchHit(
                item.id(),
                item.tenantId(),
                item.meetingOccurrenceId(),
                item.sourceItemId(),
                item.itemKind(),
                item.content(),
                score
        );
    }

    private static String key(TenantId tenantId, UUID sourceItemId, KnowledgeItemKind kind) {
        return tenantId.value() + ":" + sourceItemId + ":" + kind.name();
    }

    private static double cosine(float[] a, float[] b) {
        int n = Math.min(a.length, b.length);
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < n; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na <= 0 || nb <= 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
