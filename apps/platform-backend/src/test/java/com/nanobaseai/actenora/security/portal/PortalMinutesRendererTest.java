package com.nanobaseai.actenora.security.portal;

import com.nanobaseai.actenora.meetingintelligence.api.dto.ActionItemResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.CommitmentResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteDetailResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteVersionResponse;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNoteStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalMinutesRendererTest {

    @Test
    void rendersResolvedActionTimesAndCommitmentOwnerWithoutMisleadingMissingDateNote() {
        UUID noteId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-29T08:00:00Z");
        MeetingNoteVersionResponse version = new MeetingNoteVersionResponse(
                UUID.randomUUID(), noteId, 1, "Günlük toplantı özeti.",
                null, null, null, null, now, MeetingNoteStatus.DRAFT
        );
        List<ActionItemResponse> actions = List.of(
                action(noteId, "Oturum yenileme düzeltmesini tamamlayacak.", "Selin",
                        LocalDate.parse("2026-07-29"), "bugün 16.00'ya kadar",
                        Instant.parse("2026-07-29T13:00:00Z")),
                action(noteId, "Correlation ID ekleyecek.", "Can", null, null, null),
                action(noteId, "UTF-8 başlık düzeltmesini yapacak.", "Can", null, null, null),
                action(noteId, "Outlook ve Apple Mail regresyon testlerini tamamlayacak.", "Burak",
                        LocalDate.parse("2026-07-30"), "yarın öğlene kadar",
                        Instant.parse("2026-07-30T09:00:00Z"))
        );
        CommitmentResponse commitment = new CommitmentResponse(
                UUID.randomUUID(), noteId,
                "Test planına timeout ve retry senaryolarını ekleyeceğim.",
                "Burak", null, false, 0.9, null, null, 0, now
        );
        MeetingNoteDetailResponse note = new MeetingNoteDetailResponse(
                noteId, UUID.randomUUID(), UUID.randomUUID(), null, 1, 0,
                version, List.of(), actions, List.of(), List.of(commitment),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), now, now
        );

        String rendered = PortalApiController.renderDraftMinutesBody(note, "Daily Standup");

        assertTrue(rendered.contains("29.07.2026 16:00"));
        assertTrue(rendered.contains("30.07.2026 12:00"));
        assertTrue(rendered.contains("Sorumlu: Burak"));
        assertFalse(rendered.contains("Toplantıda aksiyonlar için kesin teslim tarihi belirtilmedi."));
        assertFalse(rendered.contains("Not: Aksiyonlar için yapılandırılmış son tarih bulunmuyor."));
    }

    private static ActionItemResponse action(
            UUID noteId,
            String text,
            String owner,
            LocalDate dueDate,
            String relativeDate,
            Instant dueAt
    ) {
        return new ActionItemResponse(
                UUID.randomUUID(), noteId, text, owner, dueDate,
                null, false, 0.9, null, "PERSON", null, relativeDate, dueAt, 0, Instant.EPOCH
        );
    }
}
