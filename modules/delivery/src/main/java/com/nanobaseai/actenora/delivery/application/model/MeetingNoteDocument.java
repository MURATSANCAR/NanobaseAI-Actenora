package com.nanobaseai.actenora.delivery.application.model;

import java.util.List;
import java.util.Objects;

/**
 * Branded meeting note payload for email + PDF rendering.
 */
public record MeetingNoteDocument(
        String meetingTitle,
        String meetingDate,
        String duration,
        String organizer,
        List<String> participants,
        String executiveSummary,
        List<String> decisions,
        List<String> actions,
        String generatedBy
) {
    public MeetingNoteDocument {
        Objects.requireNonNull(meetingTitle, "meetingTitle");
        participants = participants == null ? List.of() : List.copyOf(participants);
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
        actions = actions == null ? List.of() : List.copyOf(actions);
        generatedBy = generatedBy == null || generatedBy.isBlank() ? "Actenora · NanobaseAI" : generatedBy;
    }

    public static MeetingNoteDocument sampleDemo() {
        return new MeetingNoteDocument(
                "Nanobase Ürün Senkronu",
                "26 Temmuz 2026 · 14:00 (TR)",
                "45 dk",
                "Murat Sancar",
                List.of(
                        "Murat Sancar (Nanobase)",
                        "Ayşe Yılmaz (Nanobase)",
                        "John Smith (Acme Corp — dış katılımcı)"
                ),
                """
                Ekip, Actenora Graph entegrasyonunun canlı ortamda devreye alındığını doğruladı. \
                Toplantı sonrası otomatik transcript ve LLM tabanlı tutanak akışı test edilecek. \
                Müşteri tarafında kurulum dokümantasyonu bu hafta paylaşılacak.
                """.trim(),
                List.of(
                        "Graph calendar subscription production mailbox üzerinde aktif tutulacak.",
                        "Transcript izinleri tenant admin tarafından finalize edilecek."
                ),
                List.of(
                        "Murat — Hostinger SMTP ile branded PDF mail testini tamamla (bugün)",
                        "Ayşe — Müşteri onboarding checklist v2 (Cuma)"
                ),
                "Actenora · NanobaseAI Intelligence"
        );
    }
}
