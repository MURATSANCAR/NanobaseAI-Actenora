package com.nanobaseai.actenora.transcript.domain.normalization;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.transcript.api.TranscriptId;
import com.nanobaseai.actenora.transcript.domain.TranscriptSegment;
import com.nanobaseai.actenora.transcript.domain.dictionary.DictionaryEntry;
import com.nanobaseai.actenora.transcript.domain.dictionary.DictionaryEntryKind;
import com.nanobaseai.actenora.transcript.domain.dictionary.TenantDictionary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscriptNormalizerTest {

    private final TenantId tenantId = TenantId.random();
    private final TranscriptId transcriptId = TranscriptId.of(UUID.randomUUID());
    private final Instant now = Instant.parse("2026-07-25T12:00:00Z");

    @Test
    void dictionaryExactAndAliasMatch() {
        TenantDictionary dictionary = TenantDictionary.create(tenantId, "default", now)
                .addEntry(DictionaryEntry.of(
                        DictionaryEntryKind.PRODUCT,
                        "Actenora",
                        List.of("aktenora", "Actenora AI")), now)
                .addEntry(DictionaryEntry.of(
                        DictionaryEntryKind.COMPANY,
                        "Nanobase",
                        List.of("Nano Base")), now)
                .addEntry(DictionaryEntry.of(
                        DictionaryEntryKind.PROJECT,
                        "MeetingIQ",
                        List.of("meeting iq")), now);

        TranscriptSegment segment = segment(
                0,
                "Ali",
                "aktenora ve Nano Base ile meeting iq demosunu yaptık.");

        TranscriptNormalizer.NormalizationOutcome outcome =
                TranscriptNormalizer.normalize(List.of(segment), List.of(), dictionary);

        assertEquals(
                "Actenora ve Nanobase ile MeetingIQ demosunu yaptık.",
                outcome.segments().getFirst().normalizedContent());
        assertTrue(outcome.metrics().dictionaryRewrites() >= 3);
        assertEquals(segment.id(), outcome.segments().getFirst().originalSegmentId());
        assertEquals(segment.content(), outcome.segments().getFirst().originalContent());
    }

    @Test
    void ambiguousSpeakerIsNotAutoFinalized() {
        DictionaryEntry ayse = DictionaryEntry.of(
                DictionaryEntryKind.SPEAKER, "Ayşe Yılmaz", List.of("Ayşe"));
        DictionaryEntry ayseK = DictionaryEntry.of(
                DictionaryEntryKind.SPEAKER, "Ayşe Kaya", List.of("Ayşe"));
        TenantDictionary dictionary = TenantDictionary.create(tenantId, "default", now)
                .addEntry(ayse, now)
                .addEntry(ayseK, now);

        TranscriptSegment segment = segment(0, "Ayşe", "Merhaba");

        TranscriptNormalizer.NormalizationOutcome outcome =
                TranscriptNormalizer.normalize(List.of(segment), List.of(), dictionary);

        SpeakerResolution resolution = outcome.speakerResolutions().getFirst();
        assertEquals(SpeakerResolutionStatus.AMBIGUOUS, resolution.status());
        assertTrue(resolution.resolvedEntryId().isEmpty());
        assertEquals(2, resolution.candidateEntryIds().size());
        assertTrue(outcome.issues().stream()
                .anyMatch(i -> i.type() == NormalizationIssueType.AMBIGUOUS_SPEAKER));
        assertEquals(1, outcome.metrics().speakersAmbiguous());
        // Original speaker label preserved when ambiguous
        assertEquals("Ayşe", outcome.segments().getFirst().speakerDisplayName().orElseThrow());
    }

    @Test
    void resolvesExactAndAliasSpeakers() {
        TenantDictionary dictionary = TenantDictionary.create(tenantId, "default", now)
                .addEntry(DictionaryEntry.of(
                        DictionaryEntryKind.SPEAKER,
                        "Mehmet Öztürk",
                        List.of("Mehmet O", "m.öztürk")), now);

        TranscriptSegment exact = segment(0, "Mehmet Öztürk", "Bir");
        TranscriptSegment alias = segment(1, "m.öztürk", "İki");
        TranscriptSegment missing = segment(2, null, "Üç");

        TranscriptNormalizer.NormalizationOutcome outcome =
                TranscriptNormalizer.normalize(List.of(exact, alias, missing), List.of(), dictionary);

        assertEquals(SpeakerResolutionStatus.RESOLVED_EXACT, outcome.speakerResolutions().get(0).status());
        assertEquals(SpeakerResolutionStatus.RESOLVED_ALIAS, outcome.speakerResolutions().get(1).status());
        assertEquals(SpeakerResolutionStatus.MISSING_SPEAKER, outcome.speakerResolutions().get(2).status());
        assertEquals("Mehmet Öztürk", outcome.segments().get(1).speakerDisplayName().orElseThrow());
        assertEquals(1, outcome.metrics().speakersMissing());
    }

    @Test
    void outputHashIsDeterministic() {
        TenantDictionary dictionary = TenantDictionary.create(tenantId, "default", now)
                .addEntry(DictionaryEntry.of(
                        DictionaryEntryKind.PRODUCT, "Actenora", List.of("aktenora")), now);

        List<TranscriptSegment> segments = List.of(
                segment(0, "Ali", "aktenora hazır"),
                segment(1, "Veli", "tamam"));

        var first = TranscriptNormalizer.normalize(segments, List.of(), dictionary);
        var second = TranscriptNormalizer.normalize(segments, List.of(), dictionary);

        assertEquals(first.normalizedTranscriptHash(), second.normalizedTranscriptHash());
        assertEquals(
                first.segments().getFirst().normalizedContent(),
                second.segments().getFirst().normalizedContent());
    }

    @Test
    void hashChangesWhenNormalizedContentChanges() {
        TenantDictionary empty = TenantDictionary.create(tenantId, "default", now);
        TenantDictionary withDict = empty.addEntry(
                DictionaryEntry.of(DictionaryEntryKind.PRODUCT, "Actenora", List.of("aktenora")),
                now);

        List<TranscriptSegment> segments = List.of(segment(0, "Ali", "aktenora"));

        var without = TranscriptNormalizer.normalize(segments, List.of(), empty);
        var with = TranscriptNormalizer.normalize(segments, List.of(), withDict);

        assertNotEquals(without.normalizedTranscriptHash(), with.normalizedTranscriptHash());
    }

    private TranscriptSegment segment(int sequence, String speaker, String content) {
        return new TranscriptSegment(
                UUID.randomUUID(),
                tenantId,
                transcriptId,
                sequence,
                null,
                speaker,
                sequence * 1_000L,
                sequence * 1_000L + 500L,
                content);
    }
}
