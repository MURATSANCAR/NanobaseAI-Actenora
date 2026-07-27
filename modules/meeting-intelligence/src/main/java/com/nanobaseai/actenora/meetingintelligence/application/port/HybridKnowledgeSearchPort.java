package com.nanobaseai.actenora.meetingintelligence.application.port;

import com.nanobaseai.actenora.meetingintelligence.domain.knowledge.KnowledgeSearchHit;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;

/**
 * Hybrid retrieval: lexical FTS + vector cosine, fused with Reciprocal Rank Fusion.
 */
public interface HybridKnowledgeSearchPort {

    List<KnowledgeSearchHit> search(TenantId tenantId, String query, int limit);
}
