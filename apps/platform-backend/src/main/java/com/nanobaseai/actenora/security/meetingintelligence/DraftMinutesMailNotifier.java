package com.nanobaseai.actenora.security.meetingintelligence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Sends a draft-minutes notification immediately after LLM handoff (no approval gate).
 * Uses the configured Spring mail transport (MailHog locally, SMTP/Graph adapters in prod).
 */
public final class DraftMinutesMailNotifier {

    private static final Logger log = LoggerFactory.getLogger(DraftMinutesMailNotifier.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String fromAddress;
    private final String portalBaseUrl;

    public DraftMinutesMailNotifier(
            ObjectProvider<JavaMailSender> mailSender,
            String fromAddress,
            String portalBaseUrl
    ) {
        this.mailSender = Objects.requireNonNull(mailSender, "mailSender");
        this.fromAddress = fromAddress == null || fromAddress.isBlank()
                ? "noreply@actenora.local"
                : fromAddress.trim();
        this.portalBaseUrl = portalBaseUrl == null || portalBaseUrl.isBlank()
                ? "https://portal.nanobase.ai"
                : portalBaseUrl.trim().replaceAll("/+$", "");
    }

    public void notifyOrganizer(
            String organizerEmail,
            String meetingTitle,
            UUID meetingOccurrenceId,
            UUID noteId,
            String executiveSummary
    ) {
        if (organizerEmail == null || organizerEmail.isBlank()) {
            log.info("Draft minutes mail skipped: missing organizer email meetingId={}", meetingOccurrenceId);
            return;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("Draft minutes mail skipped: JavaMailSender not configured meetingId={}", meetingOccurrenceId);
            return;
        }
        String title = meetingTitle == null || meetingTitle.isBlank() ? "Toplantı" : meetingTitle.trim();
        String summary = executiveSummary == null || executiveSummary.isBlank()
                ? "(Özet henüz boş)"
                : executiveSummary.trim();
        String link = portalBaseUrl + "/meetings/" + meetingOccurrenceId;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(organizerEmail.trim());
        message.setSubject("[Actenora] Taslak tutanak hazır: " + title);
        message.setText(
                "Merhaba,\n\n"
                        + "\"" + title + "\" toplantısı için NanobaseAI EasyMeeting taslak tutanağı hazırlandı.\n\n"
                        + "Yönetici özeti:\n" + summary + "\n\n"
                        + "Not id: " + noteId + "\n"
                        + "Portal: " + link + "\n\n"
                        + "Bu bir taslaktır; onay sonrası final teslimat ayrı çalışır.\n"
        );
        try {
            sender.send(message);
            log.info("Draft minutes mail sent to={} meetingId={} noteId={}",
                    organizerEmail, meetingOccurrenceId, noteId);
        } catch (RuntimeException ex) {
            log.warn("Draft minutes mail failed to={} meetingId={} reason={}",
                    organizerEmail, meetingOccurrenceId, ex.getMessage());
        }
    }
}
