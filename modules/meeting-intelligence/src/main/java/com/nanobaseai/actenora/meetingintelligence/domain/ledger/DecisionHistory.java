package com.nanobaseai.actenora.meetingintelligence.domain.ledger;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Supersede chain for a decision lineage (oldest → newest).
 */
public final class DecisionHistory {

    private final TenantId tenantId;
    private final UUID rootDecisionId;
    private final List<DecisionHistoryEntry> entries;

    public DecisionHistory(TenantId tenantId, UUID rootDecisionId, List<DecisionHistoryEntry> entries) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.rootDecisionId = Objects.requireNonNull(rootDecisionId, "rootDecisionId");
        this.entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        if (this.entries.isEmpty()) {
            throw new IllegalArgumentException("history must contain at least one entry");
        }
    }

    public Optional<DecisionHistoryEntry> activeEntry() {
        return entries.stream().filter(DecisionHistoryEntry::active).reduce((a, b) -> b);
    }

    public List<DecisionHistoryEntry> supersededEntries() {
        return entries.stream().filter(e -> !e.active()).toList();
    }

    public List<DecisionHistoryEntry> chronological() {
        return entries;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public UUID rootDecisionId() {
        return rootDecisionId;
    }

    public static DecisionHistory fromChain(DecisionHistoryEntry tip, List<DecisionHistoryEntry> all) {
        Objects.requireNonNull(tip, "tip");
        List<DecisionHistoryEntry> chain = new ArrayList<>();
        DecisionHistoryEntry cursor = tip;
        while (cursor != null) {
            chain.add(0, cursor);
            Optional<UUID> older = cursor.supersedesDecisionId();
            if (older.isEmpty()) {
                break;
            }
            UUID olderId = older.get();
            cursor = all.stream()
                    .filter(e -> e.decisionId().equals(olderId))
                    .findFirst()
                    .orElse(null);
        }
        UUID root = chain.getFirst().decisionId();
        return new DecisionHistory(tip.tenantId(), root, Collections.unmodifiableList(chain));
    }
}
