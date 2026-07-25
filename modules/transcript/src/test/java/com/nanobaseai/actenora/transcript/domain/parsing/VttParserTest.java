package com.nanobaseai.actenora.transcript.domain.parsing;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.transcript.api.TranscriptId;
import com.nanobaseai.actenora.transcript.domain.TranscriptDomainException;
import com.nanobaseai.actenora.transcript.domain.TranscriptSegment;
import com.nanobaseai.actenora.transcript.domain.normalization.NormalizationIssueType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VttParserTest {

    private final TenantId tenantId = TenantId.random();
    private final TranscriptId transcriptId = TranscriptId.of(UUID.randomUUID());

    @Test
    void parsesTurkishCharactersAndMultipleSpeakers() {
        String vtt = """
                WEBVTT

                00:00:01.000 --> 00:00:03.000
                <v Ayşe Yılmaz>Çalışma planını gözden geçirelim.

                00:00:03.500 --> 00:00:06.000
                <v Mehmet Öztürk>İstanbul ofisinde toplantıyı açıyorum.
                """;

        VttParseResult result = VttParser.parse(tenantId, transcriptId, bytes(vtt));

        assertEquals(2, result.segments().size());
        assertEquals("Ayşe Yılmaz", result.segments().get(0).speakerDisplayName().orElseThrow());
        assertTrue(result.segments().get(0).content().contains("Çalışma"));
        assertEquals("Mehmet Öztürk", result.segments().get(1).speakerDisplayName().orElseThrow());
        assertTrue(result.segments().get(1).content().contains("İstanbul"));
    }

    @Test
    void allowsSegmentsWithoutSpeakerName() {
        String vtt = """
                WEBVTT

                00:00:01.000 --> 00:00:02.000
                Konuşmacı etiketi yok.
                """;

        VttParseResult result = VttParser.parse(tenantId, transcriptId, bytes(vtt));

        assertEquals(1, result.segments().size());
        assertTrue(result.segments().getFirst().speakerDisplayName().isEmpty());
        assertEquals("Konuşmacı etiketi yok.", result.segments().getFirst().content());
    }

    @Test
    void recordsMalformedTimestampWithoutAbortingOtherCues() {
        String vtt = """
                WEBVTT

                00:00:xx.000 --> 00:00:02.000
                Bozuk zaman.

                00:00:03.000 --> 00:00:04.000
                <v Ali>Geçerli satır.
                """;

        VttParseResult result = VttParser.parse(tenantId, transcriptId, bytes(vtt));

        assertEquals(1, result.segments().size());
        assertEquals("Ali", result.segments().getFirst().speakerDisplayName().orElseThrow());
        assertTrue(result.issues().stream()
                .anyMatch(i -> i.type() == NormalizationIssueType.MALFORMED_TIMESTAMP));
    }

    @Test
    void removesDuplicateSegmentsAndSortsByTime() {
        String vtt = """
                WEBVTT

                00:00:05.000 --> 00:00:06.000
                <v B>İkinci

                00:00:01.000 --> 00:00:02.000
                <v A>Birinci

                00:00:01.000 --> 00:00:02.000
                <v A>Birinci
                """;

        VttParseResult result = VttParser.parse(tenantId, transcriptId, bytes(vtt));

        assertEquals(2, result.segments().size());
        assertEquals("A", result.segments().get(0).speakerDisplayName().orElseThrow());
        assertEquals("B", result.segments().get(1).speakerDisplayName().orElseThrow());
        assertEquals(0, result.segments().get(0).sequence());
        assertEquals(1, result.segments().get(1).sequence());
        assertTrue(result.duplicatesRemoved() >= 1);
        assertTrue(result.issues().stream()
                .anyMatch(i -> i.type() == NormalizationIssueType.DUPLICATE_SEGMENT));
    }

    @Test
    void detectsOverlappingSegments() {
        String vtt = """
                WEBVTT

                00:00:01.000 --> 00:00:05.000
                <v A>Uzun

                00:00:03.000 --> 00:00:06.000
                <v B>Örtüşen
                """;

        VttParseResult result = VttParser.parse(tenantId, transcriptId, bytes(vtt));

        assertEquals(2, result.segments().size());
        assertTrue(result.issues().stream()
                .anyMatch(i -> i.type() == NormalizationIssueType.OVERLAPPING_SEGMENTS));
    }

    @Test
    void normalizesWhitespaceDeterministically() {
        String vtt = """
                WEBVTT

                00:00:01.000 --> 00:00:02.000
                <v A>  Merhaba    dünya  
                """;

        VttParseResult result = VttParser.parse(tenantId, transcriptId, bytes(vtt));

        assertEquals("Merhaba dünya", result.segments().getFirst().content());
        assertTrue(result.whitespaceNormalizedCount() >= 1);
    }

    @Test
    void rejectsNonWebvtt() {
        assertThrows(
                TranscriptDomainException.class,
                () -> VttParser.parse(tenantId, transcriptId, bytes("NOT VTT")));
    }

    @Test
    void supportsShortTimestampFormat() {
        String vtt = """
                WEBVTT

                00:01.000 --> 00:02.500
                <v A>Kısa format
                """;

        VttParseResult result = VttParser.parse(tenantId, transcriptId, bytes(vtt));
        List<TranscriptSegment> segments = result.segments();
        assertEquals(1, segments.size());
        assertEquals(1_000L, segments.getFirst().startOffsetMs());
        assertEquals(2_500L, segments.getFirst().endOffsetMs());
    }

    @Test
    void parseIsDeterministicForSameInput() {
        String vtt = """
                WEBVTT

                00:00:02.000 --> 00:00:03.000
                <v B>İkinci

                00:00:01.000 --> 00:00:01.500
                <v A>Birinci
                """;
        byte[] raw = bytes(vtt);

        VttParseResult a = VttParser.parse(tenantId, transcriptId, raw);
        VttParseResult b = VttParser.parse(tenantId, transcriptId, raw);

        assertEquals(a.segments().size(), b.segments().size());
        for (int i = 0; i < a.segments().size(); i++) {
            assertEquals(a.segments().get(i).content(), b.segments().get(i).content());
            assertEquals(a.segments().get(i).startOffsetMs(), b.segments().get(i).startOffsetMs());
            assertEquals(
                    a.segments().get(i).speakerDisplayName(),
                    b.segments().get(i).speakerDisplayName());
            assertEquals(a.segments().get(i).sequence(), b.segments().get(i).sequence());
        }
        assertEquals(a.issues().size(), b.issues().size());
        assertFalse(a.segments().isEmpty());
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
