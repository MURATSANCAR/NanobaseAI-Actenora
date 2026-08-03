package com.nanobaseai.actenora.aiprocessing.domain.pipeline.consistency;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.CommitmentCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossTypeMeetingItemSubsumerTest {

    @Test
    void whatsappClusterKeepsOneDecisionOneActionDropsCommitment() {
        List<String> evidence = List.of("seg-wa-1", "seg-wa-2");
        DecisionCandidate decision = new DecisionCandidate(
                "WhatsApp grubu oluşturulacak ve hızlı iletişim oradan yürütülecek.",
                evidence,
                0.92
        );
        List<ActionItemCandidate> actions = List.of(
                action("WhatsApp grubu oluşturulacak.", evidence),
                action("WhatsApp grubunu oluşturmak ve hızlıca yazışmak.", evidence),
                action("WhatsApp üzerinden iletişim kurulacak.", evidence)
        );
        CommitmentCandidate commitment = new CommitmentCandidate(
                "WhatsApp grubunu oluşturacağız.",
                null,
                evidence,
                0.9
        );

        CrossTypeMeetingItemSubsumer.Outcome outcome = new CrossTypeMeetingItemSubsumer().apply(
                List.of(decision),
                actions,
                List.of(commitment)
        );

        assertEquals(1, outcome.decisions().size());
        assertEquals(1, outcome.actions().size());
        assertTrue(outcome.commitments().isEmpty());
        assertTrue(outcome.commitmentsDropped() >= 1);
        assertTrue(outcome.actionsDropped() >= 2);
    }

    @Test
    void distinctWorkItemsAreNotCollapsed() {
        DecisionCandidate decision = new DecisionCandidate(
                "Pilot müşteri seçimi cuma kapanacak.",
                List.of("d1"),
                0.9
        );
        List<ActionItemCandidate> actions = List.of(
                action("MySQL okuma yetkisi açılacak.", List.of("a1")),
                action("Latency ölçümü yapılacak.", List.of("a2"))
        );
        CrossTypeMeetingItemSubsumer.Outcome outcome = new CrossTypeMeetingItemSubsumer().apply(
                List.of(decision),
                actions,
                List.of()
        );
        assertEquals(2, outcome.actions().size());
        assertEquals(0, outcome.actionsDropped());
    }

    private static ActionItemCandidate action(String text, List<String> evidence) {
        return new ActionItemCandidate(text, null, null, evidence, 0.9);
    }
}
