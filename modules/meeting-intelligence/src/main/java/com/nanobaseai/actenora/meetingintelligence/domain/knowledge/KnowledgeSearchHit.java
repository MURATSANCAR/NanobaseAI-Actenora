package com.nanobaseai.actenora.meetingintelligence.domain.knowledge;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Objects;
import java.util.UUID;

public record KnowledgeSearchHit(
        UUID itemId,
        TenantId tenantId,
        UUID meetingOccurrenceId,
        UUID sourceItemId,
        KnowledgeItemKind itemKind,
        String content,
        double score
) {
    public KnowledgeSearchHit {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        Objects.requireNonNull(sourceItemId, "sourceItemId");
        Objects.requireNonNull(itemKind, "itemKind");
        Objects.requireNonNull(content, "content");
    }
}
