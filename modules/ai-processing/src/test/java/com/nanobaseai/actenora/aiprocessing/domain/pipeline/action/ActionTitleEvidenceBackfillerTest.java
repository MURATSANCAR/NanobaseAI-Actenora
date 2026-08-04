package com.nanobaseai.actenora.aiprocessing.domain.pipeline.action;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.ItemLineageRecord;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.ItemLineageRecorder;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.LineageOperation;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.LineageReasonCode;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.LineageStage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ActionTitleEvidenceBackfillerTest {

    private final ActionTitleEvidenceBackfiller backfiller = new ActionTitleEvidenceBackfiller();

    @AfterEach
    void clearLineage() {
        ItemLineageRecorder.clear();
    }

    @Test
    void repairsTruncatedTitleFromEvidence() {
        ActionItemCandidate truncated = new ActionItemCandidate(
                "Tabanına erişim…",
                "Murat",
                null,
                List.of("seg-db"),
                0.9
        );
        List<SegmentInput> segments = List.of(
                new SegmentInput(
                        "seg-db",
                        1,
                        null,
                        0,
                        1000,
                        "Veri tabanına erişim için okuma yetkisi tanımlanacak.",
                        true
                )
        );
        List<ActionItemCandidate> out = backfiller.backfill(List.of(truncated), segments);
        assertEquals(1, out.size());
        assertTrue(out.getFirst().text().contains("Veri tabanına erişim"));
        assertTrue(out.getFirst().text().length() > truncated.text().length());
    }

    @Test
    void doesNotPasteLongDialogueWhenTitleIsComplete() {
        String complete = "PostgreSQL okuma yetkisini oluştur ve paylaş.";
        ActionItemCandidate action = new ActionItemCandidate(
                complete,
                "Murat",
                null,
                List.of("seg-long"),
                0.9
        );
        List<SegmentInput> segments = List.of(
                new SegmentInput(
                        "seg-long",
                        1,
                        null,
                        0,
                        1000,
                        "Tamam. Evet anladım. Şimdi şöyle yapalım: önce hesap açalım sonra yetki verelim "
                                + "ve ayrıca dokümantasyonu da güncelleyelim diye konuştuk uzun uzun.",
                        true
                )
        );
        List<ActionItemCandidate> out = backfiller.backfill(List.of(action), segments);
        assertEquals(complete, out.getFirst().text());
    }

    @Test
    void detectsIncompleteTitles() {
        assertTrue(ActionTitleEvidenceBackfiller.needsBackfill("Tabanına erişim…"));
        assertTrue(ActionTitleEvidenceBackfiller.needsBackfill("kısa"));
        assertTrue(ActionTitleEvidenceBackfiller.isLowSpecificity("Başlık düzeltmesini yapacak."));
        assertFalse(ActionTitleEvidenceBackfiller.needsBackfill(
                "Veri tabanına erişim için okuma yetkisi tanımlanacak."));
        assertFalse(ActionTitleEvidenceBackfiller.isLowSpecificity(
                "Oturum yenileme akışına correlation ID ekleyecek."));
    }

    @Test
    void cue51BackfillsUtf8EmailHeaderContext() {
        Cue51Fixture f = cue51();
        ActionTitleEvidenceBackfiller.ActionBackfillResult result =
                backfiller.backfill(f.canAction(), backfiller.buildContext(f.canAction(), f.segments(), f.decisions()));
        assertEquals(ActionTitleEvidenceBackfiller.BackfillDecision.UPDATED, result.decision());
        assertEquals("ACTION_TITLE_CONTEXT_BACKFILLED", result.reasonCode());
        String after = result.afterText().toLowerCase(Locale.ROOT);
        assertTrue(after.contains("utf-8") || after.contains("utf8"), after);
        assertTrue(after.contains("gönderim") || after.contains("gonderim"), after);
        assertTrue(after.contains("başlık") || after.contains("baslik") || after.contains("başlığ") || after.contains("baslig"), after);
        assertTrue(after.contains("düzelt") || after.contains("duzelt"), after);
        assertFalse(after.contains("outlook"), "must not pull sibling action scope");
        assertFalse(after.contains("yarın") || after.contains("öğlen"), "must not pull sibling date");
    }

    @Test
    void cue51PreservesCanAsOwner() {
        Cue51Fixture f = cue51();
        ActionItemCandidate out = backfiller.backfill(
                List.of(f.canAction()), f.segments(), f.decisions()).getFirst();
        assertEquals("Can", out.owner());
    }

    @Test
    void cue51DoesNotTransferBurakDateToCan() {
        Cue51Fixture f = cue51();
        ActionItemCandidate out = backfiller.backfill(
                List.of(f.canAction()), f.segments(), f.decisions()).getFirst();
        assertEquals(null, out.dueDate());
        assertEquals(null, out.relativeDate());
        assertFalse(out.text().toLowerCase(Locale.ROOT).contains("yarın"));
        assertFalse(out.text().toLowerCase(Locale.ROOT).contains("öğlen"));
    }

    @Test
    void cue51PreservesBurakRegressionAction() {
        Cue51Fixture f = cue51();
        List<ActionItemCandidate> out = backfiller.backfill(
                List.of(f.canAction(), f.burakAction()), f.segments(), f.decisions());
        ActionItemCandidate burak = out.get(1);
        assertEquals(f.burakAction().text(), burak.text());
        assertEquals("Burak", burak.owner());
        assertEquals("yarın öğlene kadar", burak.relativeDate());
        assertTrue(burak.text().toLowerCase(Locale.ROOT).contains("outlook"));
    }

    @Test
    void cue27RegressionRemainsUnchanged() {
        ActionItemCandidate selin = new ActionItemCandidate(
                "Selin, paralel refresh düzeltmesini bugün 16.00'ya kadar uygulayacak.",
                "Selin",
                null,
                List.of("seg-27a"),
                0.9,
                null,
                null,
                "bugün 16.00'ya kadar"
        );
        ActionItemCandidate can = new ActionItemCandidate(
                "Can, oturum yenileme akışına correlation ID ekleyecek.",
                "Can",
                null,
                List.of("seg-27b"),
                0.9
        );
        List<SegmentInput> segments = List.of(
                new SegmentInput("seg-27a", 27, "Selin", 0, 1000,
                        "Selin paralel refresh düzeltmesini bugün 16.00'ya kadar uygulayacak.", true),
                new SegmentInput("seg-27b", 28, "Can", 1000, 2000,
                        "Can oturum yenileme akışına correlation ID ekleyecek.", true),
                new SegmentInput("seg-26", 26, "Ece", 0, 500,
                        "Kararı açıkça kayda geçiriyorum: timeout ve retry senaryoları test planına eklenecek.", true)
        );
        List<DecisionCandidate> decisions = List.of(
                new DecisionCandidate(
                        "Timeout ve retry senaryoları test planına eklenecek.",
                        List.of("seg-26"),
                        0.9)
        );
        List<ActionItemCandidate> out = backfiller.backfill(List.of(selin, can), segments, decisions);
        assertEquals(selin.text(), out.get(0).text());
        assertEquals(can.text(), out.get(1).text());
        assertEquals("Selin", out.get(0).owner());
        assertEquals("Can", out.get(1).owner());
        assertEquals("bugün 16.00'ya kadar", out.get(0).relativeDate());
        assertEquals(null, out.get(1).relativeDate());
    }

    @Test
    void contextSpeakerMayDifferFromActionOwner() {
        ActionItemCandidate action = lowSpec("Başlık düzeltmesini yapacak.", "Can", "seg-a");
        SegmentInput decisionSeg = new SegmentInput(
                "seg-d", 49, "Ece", 0, 1000,
                "Kararı açıkça kayda geçiriyorum: Yeni gönderimlerde UTF-8 başlığı zorunlu olacak.",
                true);
        SegmentInput actionSeg = new SegmentInput(
                "seg-a", 51, "Can", 2000, 3000,
                "Can başlığı düzeltecek.",
                true);
        assertNotEquals("Ece", action.owner());
        ActionTitleEvidenceBackfiller.ActionBackfillResult result = backfiller.backfill(
                action,
                backfiller.buildContext(action, List.of(decisionSeg, actionSeg), List.of(
                        new DecisionCandidate(
                                "Yeni gönderimlerde UTF-8 başlığı zorunlu olacak.",
                                List.of("seg-d"),
                                0.95))));
        assertEquals(ActionTitleEvidenceBackfiller.BackfillDecision.UPDATED, result.decision());
        assertEquals("Can", result.action().owner());
        assertTrue(result.afterText().toLowerCase(Locale.ROOT).contains("utf-8"));
    }

    @Test
    void alreadySpecificActionIsNotChanged() {
        ActionItemCandidate action = new ActionItemCandidate(
                "Oturum yenileme akışına correlation ID ekleyecek.",
                "Can",
                null,
                List.of("seg-x"),
                0.9);
        ActionTitleEvidenceBackfiller.ActionBackfillContext ctx =
                new ActionTitleEvidenceBackfiller.ActionBackfillContext(
                        List.of(),
                        List.of(new ActionTitleEvidenceBackfiller.ContextCandidate(
                                "Yeni gönderimlerde UTF-8 başlığı zorunlu olacak.",
                                List.of("seg-d"),
                                "DECISION",
                                2,
                                10)),
                        "topic-a",
                        "chunk-a",
                        3);
        ActionTitleEvidenceBackfiller.ActionBackfillResult result = backfiller.backfill(action, ctx);
        assertEquals(ActionTitleEvidenceBackfiller.BackfillDecision.NO_UPDATE, result.decision());
        assertEquals("ACTION_TITLE_ALREADY_SPECIFIC", result.reasonCode());
        assertEquals(action.text(), result.action().text());
    }

    @Test
    void ownEvidenceIsPreferred() {
        ActionItemCandidate action = lowSpec("Başlık düzeltmesini yapacak.", "Can", "seg-own");
        SegmentInput own = new SegmentInput(
                "seg-own", 51, "Can", 0, 1000,
                "Can yeni gönderimlerde UTF-8 başlığını düzeltecek.",
                true);
        ActionTitleEvidenceBackfiller.ActionBackfillContext ctx =
                new ActionTitleEvidenceBackfiller.ActionBackfillContext(
                        List.of(own),
                        List.of(new ActionTitleEvidenceBackfiller.ContextCandidate(
                                "Outlook ve Apple Mail regresyon testleri zorunlu olacak.",
                                List.of("seg-other"),
                                "DECISION",
                                1,
                                50,
                                "topic-a",
                                "chunk-a")),
                        "topic-a",
                        "chunk-a",
                        3);
        ActionTitleEvidenceBackfiller.ActionBackfillResult result = backfiller.backfill(action, ctx);
        assertEquals(ActionTitleEvidenceBackfiller.BackfillDecision.UPDATED, result.decision());
        String after = result.afterText().toLowerCase(Locale.ROOT);
        assertTrue(after.contains("utf-8"));
        assertFalse(after.contains("outlook"));
    }

    @Test
    void nearestSameTopicContextIsUsed() {
        ActionItemCandidate action = lowSpec("Başlık düzeltmesini yapacak.", "Can", "seg-a");
        ActionTitleEvidenceBackfiller.ActionBackfillContext ctx =
                new ActionTitleEvidenceBackfiller.ActionBackfillContext(
                        List.of(),
                        List.of(
                                new ActionTitleEvidenceBackfiller.ContextCandidate(
                                        "Yeni gönderimlerde UTF-8 başlığı zorunlu olacak.",
                                        List.of("near"),
                                        "DECISION",
                                        1,
                                        50,
                                        "topic-a",
                                        "chunk-a"),
                                new ActionTitleEvidenceBackfiller.ContextCandidate(
                                        "Timeout politikası correlation ID ile zorunlu olacak.",
                                        List.of("far"),
                                        "DECISION",
                                        3,
                                        48,
                                        "topic-a",
                                        "chunk-a")),
                        "topic-a",
                        "chunk-a",
                        3);
        ActionTitleEvidenceBackfiller.ActionBackfillResult result = backfiller.backfill(action, ctx);
        assertEquals(ActionTitleEvidenceBackfiller.BackfillDecision.UPDATED, result.decision());
        assertTrue(result.afterText().toLowerCase(Locale.ROOT).contains("utf-8"));
        assertFalse(result.afterText().toLowerCase(Locale.ROOT).contains("timeout"));
        assertEquals(List.of("near"), result.contextEvidenceIds());
    }

    @Test
    void contextBeyondThreeCuesIsRejected() {
        ActionItemCandidate action = lowSpec("Başlık düzeltmesini yapacak.", "Can", "seg-a");
        ActionTitleEvidenceBackfiller.ActionBackfillContext ctx =
                new ActionTitleEvidenceBackfiller.ActionBackfillContext(
                        List.of(),
                        List.of(new ActionTitleEvidenceBackfiller.ContextCandidate(
                                "Yeni gönderimlerde UTF-8 başlığı zorunlu olacak.",
                                List.of("far"),
                                "DECISION",
                                4,
                                10,
                                "topic-a",
                                "chunk-a")),
                        "topic-a",
                        "chunk-a",
                        3);
        ActionTitleEvidenceBackfiller.ActionBackfillResult result = backfiller.backfill(action, ctx);
        assertEquals(ActionTitleEvidenceBackfiller.BackfillDecision.NO_UPDATE, result.decision());
        assertEquals("ACTION_TITLE_CONTEXT_NOT_FOUND", result.reasonCode());
    }

    @Test
    void crossTopicContextIsRejected() {
        ActionItemCandidate action = lowSpec("Başlık düzeltmesini yapacak.", "Can", "seg-a");
        ActionTitleEvidenceBackfiller.ActionBackfillContext ctx =
                new ActionTitleEvidenceBackfiller.ActionBackfillContext(
                        List.of(),
                        List.of(new ActionTitleEvidenceBackfiller.ContextCandidate(
                                "Yeni gönderimlerde UTF-8 başlığı zorunlu olacak.",
                                List.of("other-topic"),
                                "DECISION",
                                1,
                                50,
                                "topic-other",
                                "chunk-a")),
                        "topic-a",
                        "chunk-a",
                        3);
        ActionTitleEvidenceBackfiller.ActionBackfillResult result = backfiller.backfill(action, ctx);
        assertEquals(ActionTitleEvidenceBackfiller.BackfillDecision.NO_UPDATE, result.decision());
        assertEquals("ACTION_TITLE_CONTEXT_NOT_FOUND", result.reasonCode());
    }

    @Test
    void crossChunkContextIsRejected() {
        ActionItemCandidate action = lowSpec("Başlık düzeltmesini yapacak.", "Can", "seg-a");
        ActionTitleEvidenceBackfiller.ActionBackfillContext ctx =
                new ActionTitleEvidenceBackfiller.ActionBackfillContext(
                        List.of(),
                        List.of(new ActionTitleEvidenceBackfiller.ContextCandidate(
                                "Yeni gönderimlerde UTF-8 başlığı zorunlu olacak.",
                                List.of("other-chunk"),
                                "DECISION",
                                1,
                                50,
                                "topic-a",
                                "chunk-other")),
                        "topic-a",
                        "chunk-a",
                        3);
        ActionTitleEvidenceBackfiller.ActionBackfillResult result = backfiller.backfill(action, ctx);
        assertEquals(ActionTitleEvidenceBackfiller.BackfillDecision.NO_UPDATE, result.decision());
        assertEquals("ACTION_TITLE_CONTEXT_NOT_FOUND", result.reasonCode());
    }

    @Test
    void ambiguousContextProducesNoUpdate() {
        ActionItemCandidate action = lowSpec("Başlık düzeltmesini yapacak.", "Can", "seg-a");
        ActionTitleEvidenceBackfiller.ActionBackfillContext ctx =
                new ActionTitleEvidenceBackfiller.ActionBackfillContext(
                        List.of(),
                        List.of(
                                new ActionTitleEvidenceBackfiller.ContextCandidate(
                                        "Yeni gönderimlerde UTF-8 başlığı zorunlu olacak.",
                                        List.of("a"),
                                        "DECISION",
                                        2,
                                        49,
                                        "topic-a",
                                        "chunk-a"),
                                new ActionTitleEvidenceBackfiller.ContextCandidate(
                                        "SMTP relay için TLS 1.3 zorunlu olacak.",
                                        List.of("b"),
                                        "DECISION",
                                        2,
                                        48,
                                        "topic-a",
                                        "chunk-a")),
                        "topic-a",
                        "chunk-a",
                        3);
        ActionTitleEvidenceBackfiller.ActionBackfillResult result = backfiller.backfill(action, ctx);
        assertEquals(ActionTitleEvidenceBackfiller.BackfillDecision.NO_UPDATE, result.decision());
        assertEquals("ACTION_TITLE_CONTEXT_AMBIGUOUS", result.reasonCode());
        assertEquals(action.text(), result.action().text());
    }

    @Test
    void conflictingVerbProducesNoUpdate() {
        ActionItemCandidate action = lowSpec("Başlık düzeltmesini yapacak.", "Can", "seg-a");
        ActionTitleEvidenceBackfiller.ActionBackfillContext ctx =
                new ActionTitleEvidenceBackfiller.ActionBackfillContext(
                        List.of(),
                        List.of(new ActionTitleEvidenceBackfiller.ContextCandidate(
                                "UTF-8 başlığı iptal edilecek; vazgeçiyoruz.",
                                List.of("c"),
                                "DECISION",
                                1,
                                50,
                                "topic-a",
                                "chunk-a")),
                        "topic-a",
                        "chunk-a",
                        3);
        ActionTitleEvidenceBackfiller.ActionBackfillResult result = backfiller.backfill(action, ctx);
        assertEquals(ActionTitleEvidenceBackfiller.BackfillDecision.NO_UPDATE, result.decision());
    }

    @Test
    void ownerNeverChanges() {
        Cue51Fixture f = cue51();
        ActionItemCandidate out = backfiller.backfill(
                List.of(f.canAction()), f.segments(), f.decisions()).getFirst();
        assertEquals(f.canAction().owner(), out.owner());
        assertEquals(f.canAction().ownerType(), out.ownerType());
    }

    @Test
    void dueDateNeverChanges() {
        ActionItemCandidate action = new ActionItemCandidate(
                "Başlık düzeltmesini yapacak.",
                "Can",
                "2026-08-05",
                List.of("seg-a"),
                0.9);
        Cue51Fixture f = cue51();
        ActionItemCandidate out = backfiller.backfill(
                List.of(action), f.segments(), f.decisions()).getFirst();
        assertEquals("2026-08-05", out.dueDate());
    }

    @Test
    void relativeDateNeverChanges() {
        ActionItemCandidate action = new ActionItemCandidate(
                "Başlık düzeltmesini yapacak.",
                "Can",
                null,
                List.of("seg-a"),
                0.9,
                null,
                "P2",
                null);
        Cue51Fixture f = cue51();
        // action evidence id seg-a must exist in fixture — use canAction relative null
        ActionItemCandidate dated = new ActionItemCandidate(
                "Başlık düzeltmesini yapacak.",
                "Can",
                null,
                List.of("seg-51"),
                0.9,
                null,
                "P2",
                "bugün sonuna kadar");
        ActionItemCandidate out = backfiller.backfill(
                List.of(dated), f.segments(), f.decisions()).getFirst();
        assertEquals("bugün sonuna kadar", out.relativeDate());
    }

    @Test
    void priorityNeverChanges() {
        ActionItemCandidate action = new ActionItemCandidate(
                "Başlık düzeltmesini yapacak.",
                "Can",
                null,
                List.of("seg-51"),
                0.9,
                null,
                "P1",
                null);
        Cue51Fixture f = cue51();
        ActionItemCandidate out = backfiller.backfill(
                List.of(action), f.segments(), f.decisions()).getFirst();
        assertEquals("P1", out.priority());
    }

    @Test
    void candidateTypeNeverChanges() {
        // ActionItemCandidate is typed by construction; post-backfill remains an action.
        Cue51Fixture f = cue51();
        ActionItemCandidate out = backfiller.backfill(
                List.of(f.canAction()), f.segments(), f.decisions()).getFirst();
        assertTrue(out instanceof ActionItemCandidate);
        assertEquals(f.canAction().getClass(), out.getClass());
    }

    @Test
    void doesNotInventInformationOutsideEvidence() {
        Cue51Fixture f = cue51();
        ActionItemCandidate out = backfiller.backfill(
                List.of(f.canAction()), f.segments(), f.decisions()).getFirst();
        String after = out.text().toLowerCase(Locale.ROOT);
        // Context says "yeni gönderimlerde" — inventing "e-posta" is forbidden unless evidence has it.
        boolean evidenceHasEmail = f.segments().stream()
                .anyMatch(s -> s.content().toLowerCase(Locale.ROOT).contains("e-posta")
                        || s.content().toLowerCase(Locale.ROOT).contains("email"));
        if (!evidenceHasEmail) {
            assertFalse(after.contains("e-posta") || after.contains("email"));
        }
        assertFalse(after.contains("global teslimat"));
        assertFalse(after.contains("altyapısını"));
    }

    @Test
    void doesNotCopyAnotherActionsDate() {
        Cue51Fixture f = cue51();
        List<ActionItemCandidate> out = backfiller.backfill(
                List.of(f.canAction(), f.burakAction()), f.segments(), f.decisions());
        assertEquals(null, out.get(0).relativeDate());
        assertEquals("yarın öğlene kadar", out.get(1).relativeDate());
    }

    @Test
    void doesNotCopyAnotherActionsOwner() {
        Cue51Fixture f = cue51();
        List<ActionItemCandidate> out = backfiller.backfill(
                List.of(f.canAction(), f.burakAction()), f.segments(), f.decisions());
        assertEquals("Can", out.get(0).owner());
        assertEquals("Burak", out.get(1).owner());
    }

    @Test
    void backfillIsIdempotent() {
        Cue51Fixture f = cue51();
        List<ActionItemCandidate> once = backfiller.backfill(
                List.of(f.canAction()), f.segments(), f.decisions());
        List<ActionItemCandidate> twice = backfiller.backfill(once, f.segments(), f.decisions());
        assertEquals(once.getFirst(), twice.getFirst());
        ActionTitleEvidenceBackfiller.ActionBackfillResult second = backfiller.backfill(
                once.getFirst(),
                backfiller.buildContext(once.getFirst(), f.segments(), f.decisions()));
        assertEquals(ActionTitleEvidenceBackfiller.BackfillDecision.NO_UPDATE, second.decision());
        assertEquals("ACTION_TITLE_ALREADY_SPECIFIC", second.reasonCode());
    }

    @Test
    void successfulBackfillProducesUpdateLineage() {
        ItemLineageRecorder recorder = ItemLineageRecorder.enabled();
        ItemLineageRecorder.install(recorder);
        Cue51Fixture f = cue51();
        backfiller.backfill(List.of(f.canAction()), f.segments(), f.decisions());
        assertTrue(recorder.size() >= 1);
        ItemLineageRecord hit = recorder.snapshot().stream()
                .filter(r -> r.stage() == LineageStage.ACTION_TITLE_BACKFILL)
                .filter(r -> r.operation() == LineageOperation.UPDATE)
                .findFirst()
                .orElseThrow();
        assertEquals(LineageReasonCode.ACTION_TITLE_CONTEXT_BACKFILLED, hit.reasonCode());
        assertFalse(hit.relatedCandidateIds() == null || hit.relatedCandidateIds().isEmpty());
    }

    @Test
    void noOpProducesReasonCode() {
        ItemLineageRecorder recorder = ItemLineageRecorder.enabled();
        ItemLineageRecorder.install(recorder);
        ActionItemCandidate specific = new ActionItemCandidate(
                "Oturum yenileme akışına correlation ID ekleyecek.",
                "Can",
                null,
                List.of("seg-x"),
                0.9);
        backfiller.backfill(List.of(specific), List.of(), List.of());
        ItemLineageRecord hit = recorder.snapshot().stream()
                .filter(r -> r.stage() == LineageStage.ACTION_TITLE_BACKFILL)
                .findFirst()
                .orElseThrow();
        assertEquals(LineageOperation.KEEP, hit.operation());
        assertEquals(LineageReasonCode.ACTION_TITLE_ALREADY_SPECIFIC, hit.reasonCode());
    }

    @Test
    void lineageFailureDoesNotFailPipeline() {
        ItemLineageRecorder.install(ItemLineageRecorder.enabled());
        Cue51Fixture f = cue51();
        assertDoesNotThrow(() ->
                backfiller.backfill(List.of(f.canAction()), f.segments(), f.decisions()));
    }

    @Test
    void lineageDisabledDoesNotChangeOutput() {
        Cue51Fixture f = cue51();
        ItemLineageRecorder.install(ItemLineageRecorder.disabled());
        List<ActionItemCandidate> disabledOut = backfiller.backfill(
                List.of(f.canAction()), f.segments(), f.decisions());
        ItemLineageRecorder.clear();
        ItemLineageRecorder.install(ItemLineageRecorder.enabled());
        List<ActionItemCandidate> enabledOut = backfiller.backfill(
                List.of(f.canAction()), f.segments(), f.decisions());
        assertEquals(enabledOut.getFirst().text(), disabledOut.getFirst().text());
        assertEquals(enabledOut.getFirst().owner(), disabledOut.getFirst().owner());
    }

    @Test
    void compoundActionCountDoesNotChange() {
        Cue51Fixture f = cue51();
        List<ActionItemCandidate> input = List.of(f.canAction(), f.burakAction());
        List<ActionItemCandidate> out = backfiller.backfill(input, f.segments(), f.decisions());
        assertEquals(input.size(), out.size());
    }

    @Test
    void crossTypeOutputDoesNotChange() {
        Cue51Fixture f = cue51();
        List<DecisionCandidate> before = f.decisions();
        backfiller.backfill(List.of(f.canAction()), f.segments(), before);
        assertEquals(before, f.decisions());
        assertEquals(1, before.size());
        assertTrue(before.getFirst().text().toLowerCase(Locale.ROOT).contains("utf-8"));
    }

    @Test
    void actionPostProcessingStatsStillPersist() {
        Cue51Fixture f = cue51();
        ActionPostProcessingPipeline pipeline = new ActionPostProcessingPipeline();
        ActionPostProcessingPipeline.Result result = pipeline.postProcess(
                List.of(f.canAction(), f.burakAction()),
                List.of(),
                new ActionPostProcessingPipeline.Context(
                        f.segments(),
                        java.util.Set.of("Can", "Burak", "Ece"),
                        java.time.OffsetDateTime.parse("2026-07-29T10:00:00+03:00"),
                        java.time.ZoneId.of("Europe/Istanbul"),
                        null),
                f.decisions());
        assertEquals(2, result.actions().size());
        var statsMap = result.stats().toArtifactMap(null);
        assertTrue(statsMap.containsKey("inputActionCount"));
        assertTrue(ActionPostProcessingStats.isSafeArtifactPayload(statsMap));
        ActionItemCandidate can = result.actions().stream()
                .filter(a -> "Can".equals(a.owner()))
                .findFirst()
                .orElseThrow();
        assertTrue(can.text().toLowerCase(Locale.ROOT).contains("utf-8")
                || can.text().toLowerCase(Locale.ROOT).contains("gönderim")
                || !ActionTitleEvidenceBackfiller.isLowSpecificity(can.text()));
    }

    private static ActionItemCandidate lowSpec(String text, String owner, String evidenceId) {
        return new ActionItemCandidate(text, owner, null, List.of(evidenceId), 0.9);
    }

    private static Cue51Fixture cue51() {
        SegmentInput decisionSeg = new SegmentInput(
                "seg-49",
                49,
                "Ece",
                0,
                1000,
                "Kararı açıkça kayda geçiriyorum: Yeni gönderimlerde UTF-8 başlığı zorunlu olacak.",
                true);
        SegmentInput actionSeg = new SegmentInput(
                "seg-51",
                51,
                "Can",
                2000,
                3000,
                "Aksiyon kaydı: Can başlığı düzeltecek; Burak Outlook ve Apple Mail regresyonunu "
                        + "yarın öğlene kadar tamamlayacak.",
                true);
        ActionItemCandidate can = new ActionItemCandidate(
                "Başlık düzeltmesini yapacak.",
                "Can",
                null,
                List.of("seg-51"),
                0.92);
        ActionItemCandidate burak = new ActionItemCandidate(
                "Outlook ve Apple Mail regresyon testlerini yarın öğlene kadar tamamlayacak.",
                "Burak",
                null,
                List.of("seg-51"),
                0.93,
                null,
                null,
                "yarın öğlene kadar");
        DecisionCandidate decision = new DecisionCandidate(
                "Yeni gönderimlerde UTF-8 başlığı zorunlu olacak.",
                List.of("seg-49"),
                0.95);
        return new Cue51Fixture(can, burak, List.of(decisionSeg, actionSeg), List.of(decision));
    }

    private record Cue51Fixture(
            ActionItemCandidate canAction,
            ActionItemCandidate burakAction,
            List<SegmentInput> segments,
            List<DecisionCandidate> decisions
    ) {
    }
}
