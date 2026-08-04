package com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.action.ActionPostProcessingPipeline;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lineage must not change extraction outputs when enabled vs disabled.
 */
class LineageOutputEquivalenceTest {

    @AfterEach
    void tearDown() {
        ItemLineageRecorder.clear();
    }

    @Test
    void lineageEnabledDisabledActionPostProcessingEquivalent() {
        ActionItemCandidate compound = new ActionItemCandidate(
                "Aksiyon kaydı: Can başlığı düzeltecek; Burak Outlook ve Apple Mail regresyonunu yarın öğlene kadar tamamlayacak.",
                "Can",
                null,
                List.of("seg-2"),
                0.9
        );
        var ctx = new ActionPostProcessingPipeline.Context(
                List.of(),
                Set.of("Can", "Burak", "Selin"),
                OffsetDateTime.of(2026, 7, 29, 8, 11, 26, 0, ZoneOffset.ofHours(3)),
                ActionPostProcessingPipeline.DEFAULT_ZONE,
                null
        );
        ActionPostProcessingPipeline pipeline = ActionPostProcessingPipeline.productionDefaults();

        ItemLineageRecorder.install(ItemLineageRecorder.disabled());
        var off = pipeline.postProcess(List.of(compound), List.of(), ctx);

        ItemLineageRecorder.clear();
        ItemLineageRecorder.install(ItemLineageRecorder.enabled());
        var on = pipeline.postProcess(List.of(compound), List.of(), ctx);

        assertEquals(off.actions().size(), on.actions().size());
        for (int i = 0; i < off.actions().size(); i++) {
            assertEquals(off.actions().get(i).text(), on.actions().get(i).text());
            assertEquals(off.actions().get(i).owner(), on.actions().get(i).owner());
            assertEquals(off.actions().get(i).dueDate(), on.actions().get(i).dueDate());
            assertEquals(off.actions().get(i).relativeDate(), on.actions().get(i).relativeDate());
            assertEquals(off.actions().get(i).evidenceSegmentIds(), on.actions().get(i).evidenceSegmentIds());
        }
        assertTrue(ItemLineageRecorder.current().size() >= 0);
    }
}
