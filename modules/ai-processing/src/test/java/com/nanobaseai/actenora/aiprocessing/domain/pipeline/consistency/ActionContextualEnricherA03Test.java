package com.nanobaseai.actenora.aiprocessing.domain.pipeline.consistency;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionContextualEnricherA03Test {

    @Test
    void genericDuzeltmeyiYapmakGetsParalelRefreshFromDecision() {
        assertTrue(ActionContextualEnricher.isGenericAction("Düzeltmeyi yapmak."));
        FinalNoteDraft draft = new FinalNoteDraft(
                "Özet",
                List.of(new DecisionCandidate(
                        "Paralel refresh çağrıları tek promise üzerinde birleştirilecek.",
                        List.of("seg-104"),
                        0.95
                )),
                List.of(new ActionItemCandidate(
                        "Düzeltmeyi yapmak.",
                        "Selin",
                        null,
                        List.of("seg-27"),
                        0.9,
                        null,
                        null,
                        "bugün 16.00",
                        null
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("seg-104", "seg-27"),
                0.9,
                false
        );
        FinalNoteDraft out = new ActionContextualEnricher().enrich(draft, List.of());
        String text = out.actionItems().getFirst().text().toLowerCase();
        assertTrue(text.contains("paralel") || text.contains("refresh") || text.contains("promise"), text);
        assertFalse(text.matches("düzeltmeyi yapmak\\.?"));
        assertTrue(text.contains("düzelt"), text);
    }
}
