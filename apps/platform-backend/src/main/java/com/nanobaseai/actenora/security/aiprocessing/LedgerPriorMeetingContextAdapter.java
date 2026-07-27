package com.nanobaseai.actenora.security.aiprocessing;

import com.nanobaseai.actenora.aiprocessing.application.pipeline.PriorMeetingContext;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.PriorMeetingContextPort;
import com.nanobaseai.actenora.meetingintelligence.api.ledger.ContinuityLedgerApi;
import com.nanobaseai.actenora.meetingintelligence.application.port.HybridKnowledgeSearchPort;
import com.nanobaseai.actenora.meetingintelligence.domain.knowledge.KnowledgeSearchHit;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.MeetingBrief;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Maps continuity ledger briefs + hybrid knowledge hits into extraction prior context.
 */
public final class LedgerPriorMeetingContextAdapter implements PriorMeetingContextPort {

    private static final int KNOWLEDGE_LIMIT = 8;

    private final ContinuityLedgerApi ledgerApi;
    private final HybridKnowledgeSearchPort knowledgeSearch;

    public LedgerPriorMeetingContextAdapter(ContinuityLedgerApi ledgerApi) {
        this(ledgerApi, null);
    }

    public LedgerPriorMeetingContextAdapter(
            ContinuityLedgerApi ledgerApi,
            HybridKnowledgeSearchPort knowledgeSearch
    ) {
        this.ledgerApi = Objects.requireNonNull(ledgerApi, "ledgerApi");
        this.knowledgeSearch = knowledgeSearch;
    }

    @Override
    public Optional<PriorMeetingContext> load(TenantId tenantId, UUID meetingOccurrenceId) {
        MeetingBrief brief = ledgerApi.generateBrief(tenantId, meetingOccurrenceId);
        List<String> openTasks = brief.openTasks().stream().map(MeetingBrief.CarryOverItem::text).toList();
        List<String> openRisks = brief.openRisks().stream().map(MeetingBrief.CarryOverItem::text).toList();
        List<String> unresolved = brief.unresolvedQuestions().stream()
                .map(MeetingBrief.CarryOverItem::text).toList();
        List<String> decisions = brief.activeDecisions().stream().map(d -> d.text()).toList();
        List<String> commitments = brief.overdueCommitments().stream().map(c -> c.text()).toList();
        List<String> related = loadRelatedKnowledge(
                tenantId,
                Stream.of(openTasks, openRisks, unresolved, decisions, commitments)
                        .flatMap(List::stream)
                        .collect(Collectors.toCollection(ArrayList::new))
        );

        PriorMeetingContext context = new PriorMeetingContext(
                brief.previousOccurrenceId(),
                openTasks,
                openRisks,
                unresolved,
                decisions,
                commitments,
                related
        );
        return context.isEmpty() ? Optional.empty() : Optional.of(context);
    }

    private List<String> loadRelatedKnowledge(TenantId tenantId, List<String> seeds) {
        if (knowledgeSearch == null || seeds.isEmpty()) {
            return List.of();
        }
        String query = String.join(" ", seeds);
        if (query.isBlank()) {
            return List.of();
        }
        Set<String> known = new LinkedHashSet<>(seeds);
        List<String> related = new ArrayList<>();
        try {
            for (KnowledgeSearchHit hit : knowledgeSearch.search(tenantId, query, KNOWLEDGE_LIMIT)) {
                if (known.add(hit.content())) {
                    related.add("[" + hit.itemKind().name() + "] " + hit.content());
                }
            }
        } catch (RuntimeException ignored) {
            return List.of();
        }
        return List.copyOf(related);
    }
}
