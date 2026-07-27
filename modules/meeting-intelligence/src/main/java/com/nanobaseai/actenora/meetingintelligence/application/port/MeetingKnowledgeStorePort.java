package com.nanobaseai.actenora.meetingintelligence.application.port;

import com.nanobaseai.actenora.meetingintelligence.domain.knowledge.KnowledgeItemKind;
import com.nanobaseai.actenora.meetingintelligence.domain.knowledge.KnowledgeSearchHit;
import com.nanobaseai.actenora.meetingintelligence.domain.knowledge.MeetingKnowledgeItem;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.UUID;

public interface MeetingKnowledgeStorePort {

    void upsert(MeetingKnowledgeItem item);

    List<KnowledgeSearchHit> searchFts(TenantId tenantId, String query, int limit);

    List<KnowledgeSearchHit> searchVector(TenantId tenantId, float[] embedding, int limit);

    List<MeetingKnowledgeItem> findByOccurrence(TenantId tenantId, UUID meetingOccurrenceId);

    List<MeetingKnowledgeItem> findBySource(TenantId tenantId, UUID sourceItemId, KnowledgeItemKind kind);
}
