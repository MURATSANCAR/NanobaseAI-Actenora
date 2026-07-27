package com.nanobaseai.actenora.security.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobaseai.actenora.meetingintelligence.api.dto.ActionItemResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.DecisionResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteDetailResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteVersionResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.RiskResponse;
import com.nanobaseai.actenora.meetingintelligence.domain.model.ActionItemStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNoteStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.model.NoteReviewStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.model.NoteVersionSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovedNoteContentJsonMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @Test
    void buildsAgendaAndExpandedAttrs() throws Exception {
        UUID noteId = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        MeetingNoteVersionResponse version = new MeetingNoteVersionResponse(
                UUID.randomUUID(),
                noteId,
                1,
                "Gündem: Roadmap — Q3; Budget. 1 karar kaydedildi.",
                NoteVersionSource.AI_MAPPING,
                null,
                null,
                UUID.randomUUID(),
                now,
                MeetingNoteStatus.APPROVED
        );
        MeetingNoteDetailResponse note = new MeetingNoteDetailResponse(
                noteId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                NoteReviewStatus.ACTIVE,
                1,
                1L,
                version,
                List.of(new DecisionResponse(
                        UUID.randomUUID(), noteId, "Ship it", null, null, false, 0.9, null,
                        "Need velocity", "DECIDED", 1L, now
                )),
                List.of(new ActionItemResponse(
                        UUID.randomUUID(), noteId, "Write docs", "Ada", LocalDate.parse("2026-02-01"),
                        ActionItemStatus.OPEN, false, 0.8, null, "PERSON", "HIGH", "next week",
                        1L, now
                )),
                List.of(new RiskResponse(
                        UUID.randomUUID(), noteId, "Slippage", false, 0.7, null,
                        "HIGH", "Add buffer", 1L, now
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                now,
                now
        );

        JsonNode root = MAPPER.readTree(ApprovedNoteContentJsonMapper.toContentJson(note, "Weekly"));
        assertEquals(2, root.path("agenda").size());
        assertEquals("Roadmap — Q3", root.path("agenda").get(0).path("item").asText());
        assertEquals("Need velocity", root.path("decisions").get(0).path("rationale").asText());
        assertEquals("PERSON", root.path("actions").get(0).path("ownerType").asText());
        assertEquals("HIGH", root.path("risks").get(0).path("likelihood").asText());
        assertTrue(root.path("agenda").isArray());
    }

    @Test
    void parseAgendaItems_emptyWithoutPrefix() {
        assertTrue(ApprovedNoteContentJsonMapper.parseAgendaItems("Just a summary.").isEmpty());
    }

    @Test
    void parseAgendaItems_numberedMultiline() {
        String summary = """
                Gündem:
                1. Sprint planlama ve kapasite
                2. Ürün gereksinimleri ve filtre davranışı
                3. Toplantı yönetimi ve teknik ayarlar

                3 karar kaydedildi.
                2 aksiyon maddesi.
                2 risk.
                """;
        assertEquals(
                List.of(
                        "Sprint planlama ve kapasite",
                        "Ürün gereksinimleri ve filtre davranışı",
                        "Toplantı yönetimi ve teknik ayarlar"
                ),
                ApprovedNoteContentJsonMapper.parseAgendaItems(summary)
        );
    }

    @Test
    void parseAgendaItems_legacySemicolon() {
        assertEquals(
                List.of("Roadmap — Q3", "Budget"),
                ApprovedNoteContentJsonMapper.parseAgendaItems("Gündem: Roadmap — Q3; Budget. 1 karar kaydedildi.")
        );
    }
}
