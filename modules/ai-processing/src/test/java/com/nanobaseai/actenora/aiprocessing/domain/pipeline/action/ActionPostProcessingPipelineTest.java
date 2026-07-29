package com.nanobaseai.actenora.aiprocessing.domain.pipeline.action;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.CommitmentCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionPostProcessingPipelineTest {

    private static final ZoneId IST = ZoneId.of("Europe/Istanbul");
    private static final OffsetDateTime MEETING_START =
            OffsetDateTime.parse("2026-07-29T08:11:26+03:00");

    private final ActionPostProcessingPipeline pipeline = ActionPostProcessingPipeline.productionDefaults();
    private final ActionDiscoursePrefixNormalizer prefix = new ActionDiscoursePrefixNormalizer();
    private final TurkishRelativeDateResolver resolver = new TurkishRelativeDateResolver();
    private final ActionDeduplicator deduplicator = new ActionDeduplicator();
    private final ActionExtractionAuditor auditor = new ActionExtractionAuditor();

    @Test
    void actionPrefixIsRemoved() {
        assertEquals(
                "Selin düzeltmeyi bugün 16.00'ya kadar yapacak; Can correlation id ekleyecek.",
                prefix.strip("Aksiyon kaydı: Selin düzeltmeyi bugün 16.00'ya kadar yapacak; Can correlation id ekleyecek.")
        );
    }

    @Test
    void prefixRemovalIsCaseInsensitive() {
        assertEquals("Selin işi yapacak.", prefix.strip("aksİyon KAYDI: Selin işi yapacak."));
    }

    @Test
    void prefixInsideSentenceIsNotRemoved() {
        String text = "Can dedi ki aksiyon kaydı: yarın bakacağız.";
        assertEquals(text, prefix.strip(text));
    }

    @Test
    void sourceEvidenceIsPreservedAfterPrefixRemoval() {
        ActionItemCandidate in = new ActionItemCandidate(
                "Aksiyon kaydı: Can correlation id ekleyecek.",
                "Can",
                null,
                List.of("seg-27"),
                0.9,
                "PERSON",
                null,
                null,
                null
        );
        var result = pipeline.postProcess(List.of(in), List.of(), ctx(List.of()));
        assertEquals(List.of("seg-27"), result.actions().getFirst().evidenceSegmentIds());
        assertFalse(result.actions().getFirst().text().startsWith("Aksiyon"));
    }

    @Test
    void turkishSemicolonCompoundActionIsSplit() {
        ActionItemCandidate in = action(
                "Selin düzeltmeyi bugün 16.00'ya kadar yapacak; Can correlation id ekleyecek.",
                "Selin",
                List.of("s1")
        );
        var result = pipeline.postProcess(List.of(in), List.of(), ctx(segments()));
        assertEquals(2, result.actions().size());
        assertEquals("Selin", result.actions().get(0).owner());
        assertEquals("Can", result.actions().get(1).owner());
    }

    @Test
    void twoOwnersProduceTwoAtomicActions() {
        ActionItemCandidate in = action(
                "Can başlığı düzeltecek; Burak Outlook ve Apple Mail regresyonunu yarın öğlene kadar tamamlayacak.",
                "Can",
                List.of("s2")
        );
        var result = pipeline.postProcess(List.of(in), List.of(), ctx(segments()));
        assertEquals(2, result.actions().size());
        assertTrue(result.actions().stream().anyMatch(a -> "Burak".equals(a.owner())));
        assertTrue(result.actions().stream().anyMatch(a -> "Can".equals(a.owner())));
    }

    @Test
    void compoundParentIsRemovedAfterSuccessfulSplit() {
        ActionItemCandidate in = action(
                "Aksiyon kaydı: Selin düzeltmeyi bugün 16.00'ya kadar yapacak; Can correlation id ekleyecek.",
                "Selin",
                List.of("s1")
        );
        var result = pipeline.postProcess(List.of(in), List.of(), ctx(segments()));
        assertTrue(result.actions().stream().noneMatch(a -> a.text().contains(";")));
        assertTrue(result.actions().stream().noneMatch(a -> a.text().startsWith("Aksiyon")));
    }

    @Test
    void ambiguousSemicolonDoesNotForceSplit() {
        ActionItemCandidate in = action(
                "Listeyi şunlarla güncelle: timeout; retry; yetkisiz erişim.",
                "Burak",
                List.of("s3")
        );
        var result = pipeline.postProcess(List.of(in), List.of(), ctx(segments()));
        assertEquals(1, result.actions().size());
        assertTrue(result.qualityFlags().contains(CompoundActionDecomposer.AMBIGUOUS_SPLIT)
                || result.actions().getFirst().text().contains(";"));
    }

    @Test
    void evidenceIdsAreCopiedToChildren() {
        ActionItemCandidate in = action(
                "Selin düzeltmeyi yapacak; Can correlation id ekleyecek.",
                "Selin",
                List.of("ev-a", "ev-b")
        );
        var result = pipeline.postProcess(List.of(in), List.of(), ctx(segments()));
        assertEquals(2, result.actions().size());
        for (ActionItemCandidate child : result.actions()) {
            assertEquals(List.of("ev-a", "ev-b"), child.evidenceSegmentIds());
        }
    }

    @Test
    void todayAt1600BindsOnlyToSelinClause() {
        ActionItemCandidate in = action(
                "Selin düzeltmeyi bugün 16.00'ya kadar yapacak; Can correlation id ekleyecek.",
                "Selin",
                List.of("s1")
        );
        var result = pipeline.postProcess(List.of(in), List.of(), ctx(segments()));
        ActionItemCandidate selin = result.actions().stream()
                .filter(a -> "Selin".equals(a.owner())).findFirst().orElseThrow();
        ActionItemCandidate can = result.actions().stream()
                .filter(a -> "Can".equals(a.owner())).findFirst().orElseThrow();
        assertNotNull(selin.relativeDate());
        assertTrue(selin.relativeDate().toLowerCase().contains("bugün")
                || selin.relativeDate().toLowerCase().contains("16"));
        assertNull(can.relativeDate());
        assertNull(can.dueAt());
    }

    @Test
    void tomorrowNoonBindsOnlyToBurakClause() {
        ActionItemCandidate in = action(
                "Can başlığı düzeltecek; Burak Outlook ve Apple Mail regresyonunu yarın öğlene kadar tamamlayacak.",
                "Can",
                List.of("s2")
        );
        var result = pipeline.postProcess(List.of(in), List.of(), ctx(segments()));
        ActionItemCandidate burak = result.actions().stream()
                .filter(a -> "Burak".equals(a.owner())).findFirst().orElseThrow();
        ActionItemCandidate can = result.actions().stream()
                .filter(a -> "Can".equals(a.owner())).findFirst().orElseThrow();
        assertNotNull(burak.relativeDate());
        assertTrue(burak.relativeDate().toLowerCase().contains("yarın")
                || burak.relativeDate().toLowerCase().contains("öğlen"));
        assertNull(can.relativeDate());
    }

    @Test
    void ownerAndDateDoNotLeakBetweenClauses() {
        todayAt1600BindsOnlyToSelinClause();
        tomorrowNoonBindsOnlyToBurakClause();
    }

    @Test
    void resolvesBugun1600InEuropeIstanbul() {
        var resolved = resolver.resolve("bugün 16.00'ya kadar", MEETING_START, IST);
        assertEquals(TurkishRelativeDateResolver.Status.RESOLVED, resolved.status());
        assertTrue(resolved.dueAt().toString().startsWith("2026-07-29T16:00"));
        assertEquals("+03:00", resolved.dueAt().getOffset().toString());
    }

    @Test
    void resolvesYarinOgleneKadar() {
        var resolved = resolver.resolve("yarın öğlene kadar", MEETING_START, IST);
        assertEquals(TurkishRelativeDateResolver.Status.RESOLVED, resolved.status());
        assertTrue(resolved.dueAt().toString().startsWith("2026-07-30T12:00"));
    }

    @Test
    void preservesUnsupportedRelativeDateForReview() {
        var resolved = resolver.resolve("ay sonunda bir ara", MEETING_START, IST);
        assertEquals(TurkishRelativeDateResolver.Status.UNRESOLVED, resolved.status());
        assertNull(resolved.dueAt());
        assertEquals("ay sonunda bir ara", resolved.relativeDateText());
    }

    @Test
    void meetingStartedAtIsUsedAsReferenceDate() {
        var resolved = resolver.resolve("yarın", OffsetDateTime.parse("2026-01-01T10:00:00+03:00"), IST);
        assertEquals("2026-01-02", resolved.dueAt().toLocalDate().toString());
    }

    @Test
    void timezoneIsPreservedInDueAt() {
        var resolved = resolver.resolve("bugün 16:00", MEETING_START, IST);
        assertEquals("+03:00", resolved.dueAt().getOffset().toString());
    }

    @Test
    void duplicateCorrelationIdActionsAreMerged() {
        List<ActionItemCandidate> actions = List.of(
                action("Aksiyon kaydı: Selin düzeltmeyi bugün 16.00'ya kadar yapacak; Can correlation id ekleyecek.",
                        "Selin", List.of("s1")),
                action("Can, correlation ID eklemesini gerçekleştirecek.", "Can", List.of("s1"))
        );
        var result = pipeline.postProcess(actions, List.of(), ctx(segments()));
        long canActions = result.actions().stream().filter(a -> "Can".equals(a.owner())).count();
        assertEquals(1, canActions);
    }

    @Test
    void atomicActionWinsOverCompoundParent() {
        duplicateCorrelationIdActionsAreMerged();
        var result = pipeline.postProcess(List.of(
                action("Aksiyon kaydı: Selin düzeltmeyi yapacak; Can correlation id ekleyecek.", "Selin", List.of("s1")),
                action("Can, correlation ID eklemesini gerçekleştirecek.", "Can", List.of("s1"))
        ), List.of(), ctx(segments()));
        assertTrue(result.actions().stream().noneMatch(a -> a.text().contains(";")));
    }

    @Test
    void dueDateAndEvidenceAreMergedIntoSurvivor() {
        ActionItemCandidate a = new ActionItemCandidate(
                "Correlation ID ekleyecek.", "Can", null, List.of("s1"), 0.8, null, null, null,
                "2026-07-29T16:00:00+03:00");
        ActionItemCandidate b = new ActionItemCandidate(
                "Can, correlation ID eklemesini gerçekleştirecek.", "Can", null, List.of("s2"), 0.7,
                null, null, "bugün", null);
        var dedup = deduplicator.deduplicate(List.of(a, b));
        assertEquals(1, dedup.actions().size());
        ActionItemCandidate survivor = dedup.actions().getFirst();
        assertNotNull(survivor.dueAt());
        assertTrue(survivor.evidenceSegmentIds().containsAll(List.of("s1", "s2")));
    }

    @Test
    void differentOwnersAreNotDeduplicated() {
        var dedup = deduplicator.deduplicate(List.of(
                action("Correlation ID ekleyecek.", "Can", List.of("s1")),
                action("Correlation ID ekleyecek.", "Selin", List.of("s1"))
        ));
        assertEquals(2, dedup.actions().size());
    }

    @Test
    void sameOwnerDifferentActionsAreNotDeduplicated() {
        var dedup = deduplicator.deduplicate(List.of(
                action("Correlation ID ekleyecek.", "Can", List.of("s1")),
                action("UTF-8 başlık düzeltmesini yapacak.", "Can", List.of("s2"))
        ));
        assertEquals(2, dedup.actions().size());
    }

    @Test
    void standupEvalFixtureProducesExpectedActions() {
        List<SegmentInput> segs = segments();
        List<ActionItemCandidate> llmOut = List.of(
                action("Aksiyon kaydı: Selin düzeltmeyi bugün 16.00'ya kadar yapacak; Can correlation id ekleyecek.",
                        "Selin", List.of("s1")),
                action("Can, correlation ID eklemesini gerçekleştirecek.", "Can", List.of("s1")),
                action("Aksiyon kaydı: Can başlığı düzeltecek; Burak Outlook ve Apple Mail regresyonunu yarın öğlene kadar tamamlayacak.",
                        "Can", List.of("s2")),
                action("Can, UTF-8 başlık düzeltmesini yapacak.", "Can", List.of("s2"))
        );
        CommitmentCandidate commitment = new CommitmentCandidate(
                "Test planına mutlu yol dışında timeout, retry, yetkisiz erişim ve yarıda kalan işlem senaryolarını ekleyeceğim.",
                null,
                List.of("s3"),
                0.9
        );
        var result = pipeline.postProcess(llmOut, List.of(commitment), ctx(segs));

        assertTrue(result.actions().stream().noneMatch(a -> a.text().contains("Aksiyon kaydı")));
        assertTrue(result.actions().stream().noneMatch(a -> a.text().contains(";")));
        long canCorrelation = result.actions().stream()
                .filter(a -> "Can".equals(a.owner()))
                .filter(a -> a.text().toLowerCase().contains("correlation"))
                .count();
        assertEquals(1, canCorrelation);

        ActionItemCandidate selin = result.actions().stream()
                .filter(a -> "Selin".equals(a.owner())).findFirst().orElseThrow();
        assertNotNull(selin.dueAt());
        assertTrue(selin.dueAt().startsWith("2026-07-29T16:00:00"));

        ActionItemCandidate burak = result.actions().stream()
                .filter(a -> "Burak".equals(a.owner())).findFirst().orElseThrow();
        assertNotNull(burak.dueAt());
        assertTrue(burak.dueAt().startsWith("2026-07-30T12:00:00"));

        assertEquals("Burak", result.commitments().getFirst().owner());
        assertFalse(result.actions().stream().anyMatch(a ->
                a.text().equalsIgnoreCase("Can, correlation ID eklemesini gerçekleştirecek.")));
    }

    @Test
    void liveShapeCompoundChildAndParaphraseAreDeduplicated() {
        List<ActionItemCandidate> llmOut = List.of(
                action("Aksiyon kaydı: Selin düzeltmeyi bugün 16.00'ya kadar yapacak; Can correlation id ekleyecek.",
                        "Selin", List.of("s1")),
                action("Can, correlation ID eklemesini gerçekleştirecek.", "Can,", List.of("s3")),
                action("Can başlığı düzeltecek.", "Can", List.of("s2")),
                action("Burak, Outlook ve Apple Mail regresyon testlerini yarın öğlene kadar tamamlayacak.",
                        "Burak", List.of("s2"))
        );
        var result = pipeline.postProcess(llmOut, List.of(), ctx(segments()));
        long correlationCount = result.actions().stream()
                .filter(a -> "can".equalsIgnoreCase(a.owner()))
                .filter(a -> a.text().toLowerCase().contains("correlation"))
                .count();
        assertEquals(1, correlationCount);
    }

    @Test
    void paraphraseDedupProducesFourActions() {
        List<ActionItemCandidate> llmOut = List.of(
                action("Aksiyon kaydı: Selin düzeltmeyi bugün 16.00'ya kadar yapacak; Can correlation id ekleyecek.",
                        "Selin", List.of("s1")),
                action("Can, correlation ID eklemesini gerçekleştirecek.", "Can:", List.of("s3")),
                action("Can başlığı düzeltecek.", "Can", List.of("s2")),
                action("Burak, Outlook ve Apple Mail regresyon testlerini yarın öğlene kadar tamamlayacak.",
                        "Burak", List.of("s2"))
        );
        var result = pipeline.postProcess(llmOut, List.of(), ctx(segments()));
        assertEquals(4, result.actions().size());
        assertEquals(1, result.stats().duplicatesRemoved());
    }

    @Test
    void dedupPreservesDueAtAndEvidence() {
        List<ActionItemCandidate> llmOut = List.of(
                new ActionItemCandidate(
                        "Can correlation id ekleyecek.",
                        "Can",
                        null,
                        List.of("s1"),
                        0.7,
                        "PERSON",
                        null,
                        null,
                        null
                ),
                new ActionItemCandidate(
                        "Can, correlation ID eklemesini gerçekleştirecek.",
                        "Can,",
                        "2026-07-29",
                        List.of("s3"),
                        0.9,
                        "PERSON",
                        null,
                        "bugün 16.00'ya kadar",
                        "2026-07-29T16:00:00+03:00"
                )
        );
        var result = pipeline.postProcess(llmOut, List.of(), ctx(segments()));
        assertEquals(1, result.actions().size());
        ActionItemCandidate survivor = result.actions().getFirst();
        assertEquals("2026-07-29", survivor.dueDate());
        assertEquals("2026-07-29T16:00:00+03:00", survivor.dueAt());
        assertTrue(survivor.evidenceSegmentIds().containsAll(List.of("s1", "s3")));
    }

    @Test
    void duplicateRemovalIsReportedInStats() {
        List<ActionItemCandidate> llmOut = List.of(
                action("Can correlation id ekleyecek.", "Can", List.of("s1")),
                action("Can, correlation ID eklemesini gerçekleştirecek.", "Can,", List.of("s3"))
        );
        var result = pipeline.postProcess(llmOut, List.of(), ctx(segments()));
        assertEquals(1, result.stats().duplicatesRemoved());
        assertEquals(1, result.actions().size());
        assertFalse(result.stats().actionTrace().isEmpty());
    }

    @Test
    void auditFailsOnPrefixLeak() {
        var audit = auditor.audit(List.of(action("Aksiyon kaydı: Can işi yapacak.", "Can", List.of("s1"))));
        assertFalse(audit.passed());
        assertTrue(audit.flags().contains(ActionExtractionAuditor.PREFIX_LEAK));
    }

    @Test
    void auditFailsOnUnsplitCompoundAction() {
        var audit = auditor.audit(List.of(action(
                "Selin düzeltmeyi yapacak; Can correlation id ekleyecek.", "Selin", List.of("s1"))));
        assertFalse(audit.passed());
        assertTrue(audit.flags().contains(ActionExtractionAuditor.UNSPLIT_COMPOUND));
    }

    @Test
    void auditFailsWhenDateCueExistsButStructuredDateIsMissing() {
        var audit = auditor.audit(List.of(action(
                "Selin düzeltmeyi bugün 16.00'ya kadar yapacak.", "Selin", List.of("s1"))));
        assertFalse(audit.passed());
        assertTrue(audit.flags().contains(ActionExtractionAuditor.DATE_CUE_MISSING_STRUCTURED));
    }

    @Test
    void auditFailsOnDuplicateAction() {
        var audit = auditor.audit(List.of(
                action("Correlation ID ekleyecek.", "Can", List.of("s1")),
                action("Can, correlation ID eklemesini gerçekleştirecek.", "Can", List.of("s1"))
        ));
        assertFalse(audit.passed());
        assertTrue(audit.flags().contains(ActionExtractionAuditor.DUPLICATE_ACTION));
    }

    @Test
    void decisionAuditCanPassWhileOverallAuditFails() {
        var audit = auditor.audit(List.of(action("Aksiyon kaydı: Can işi yapacak.", "Can", List.of("s1"))));
        assertFalse(audit.passed());
        assertTrue(audit.flags().contains(ActionExtractionAuditor.OVERALL_FAILED));
        List<String> remapped = auditor.remapDecisionFlags(List.of("CONSISTENCY_AUDIT_PASSED"), false);
        assertTrue(remapped.contains(ActionExtractionAuditor.DECISION_PASSED));
    }

    @Test
    void relativeDateIsNotDroppedBySynthesisCandidateShape() {
        // Guardrail: ActionItemCandidate retains relativeDate + dueAt fields end-to-end.
        ActionItemCandidate a = new ActionItemCandidate(
                "Düzeltmeyi yapacak.", "Selin", "2026-07-29", List.of("s1"), 0.9,
                "PERSON", null, "bugün 16.00'ya kadar", "2026-07-29T16:00:00+03:00");
        assertEquals("bugün 16.00'ya kadar", a.relativeDate());
        assertEquals("2026-07-29T16:00:00+03:00", a.dueAt());
    }

    @Test
    void nonIsoRelativeDateIsNotSilentlyDiscarded() {
        var resolved = resolver.resolve("önümüzdeki hafta", MEETING_START, IST);
        assertEquals(TurkishRelativeDateResolver.Status.RESOLVED, resolved.status());
        assertNotNull(resolved.relativeDateText());
    }

    @Test
    void commitmentOwnerBoundFromFirstPersonSpeaker() {
        CommitmentCandidate c = new CommitmentCandidate(
                "Test planına mutlu yol dışında timeout, retry, yetkisiz erişim ve yarıda kalan işlem senaryolarını ekleyeceğim.",
                null,
                List.of("s3"),
                0.9
        );
        var result = pipeline.postProcess(List.of(), List.of(c), ctx(segments()));
        assertEquals("Burak", result.commitments().getFirst().owner());
    }

    private static ActionItemCandidate action(String text, String owner, List<String> evidence) {
        return new ActionItemCandidate(text, owner, null, evidence, 0.9, "PERSON", null, null, null);
    }

    private static ActionPostProcessingPipeline.Context ctx(List<SegmentInput> segments) {
        return new ActionPostProcessingPipeline.Context(
                segments,
                Set.of("Selin", "Can", "Burak", "Ece", "Derya"),
                MEETING_START,
                IST,
                "meeting-eval-15dk"
        );
    }

    private static List<SegmentInput> segments() {
        return List.of(
                new SegmentInput("s1", 1, "Derya", 0, 1000,
                        "Aksiyon kaydı: Selin düzeltmeyi bugün 16.00'ya kadar yapacak; Can correlation id ekleyecek.",
                        true),
                new SegmentInput("s2", 2, "Can", 1000, 2000,
                        "Aksiyon kaydı: Can başlığı düzeltecek; Burak Outlook ve Apple Mail regresyonunu yarın öğlene kadar tamamlayacak. UTF-8 başlığı zorunlu olacak.",
                        true),
                new SegmentInput("s3", 3, "Burak", 2000, 3000,
                        "Test planına mutlu yol dışında timeout, retry, yetkisiz erişim ve yarıda kalan işlem senaryolarını ekleyeceğim.",
                        false)
        );
    }
}
