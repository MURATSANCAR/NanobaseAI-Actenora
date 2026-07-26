package com.nanobaseai.actenora.delivery.infrastructure.mail;

import com.nanobaseai.actenora.delivery.application.model.MeetingNoteDocument;
import com.nanobaseai.actenora.delivery.application.port.DeliveryMailProvider;
import com.nanobaseai.actenora.delivery.domain.DeliveryDomainException;
import com.nanobaseai.actenora.delivery.domain.DeliveryPolicySnapshot;
import com.nanobaseai.actenora.delivery.domain.ProviderMessage;
import com.nanobaseai.actenora.delivery.infrastructure.render.MeetingNoteBrandedTemplates;
import com.nanobaseai.actenora.delivery.infrastructure.render.MeetingNotePdfRenderer;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Production SMTP mail provider (Hostinger and similar). Sends HTML body with optional PDF attachment.
 */
public final class SmtpMailProvider implements DeliveryMailProvider {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String fromDisplayName;
    private final Supplier<Instant> clock;
    private final MeetingNotePdfRenderer pdfRenderer;

    public SmtpMailProvider(
            JavaMailSender mailSender,
            String fromAddress,
            String fromDisplayName,
            Supplier<Instant> clock
    ) {
        this.mailSender = Objects.requireNonNull(mailSender, "mailSender");
        this.fromAddress = Objects.requireNonNull(fromAddress, "fromAddress");
        this.fromDisplayName = fromDisplayName == null || fromDisplayName.isBlank()
                ? "Nanobase Actenora"
                : fromDisplayName;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.pdfRenderer = new MeetingNotePdfRenderer();
    }

    @Override
    public String providerType() {
        return DeliveryPolicySnapshot.PROVIDER_SMTP;
    }

    @Override
    public void validateConfiguration() {
        if (fromAddress.isBlank()) {
            throw new DeliveryDomainException("PROVIDER_CONFIG_INVALID", "SMTP from address is required");
        }
    }

    @Override
    public ProviderStatus getProviderStatus() {
        return new ProviderStatus(true, providerType(), "smtp:" + fromAddress);
    }

    @Override
    public SendResult send(SendCommand command) {
        validateConfiguration();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, fromDisplayName);
            helper.setTo(command.request().recipient().email());
            helper.setSubject(command.request().subject());

            String plain = command.request().bodyText();
            String html = MeetingNoteBrandedTemplates.emailHtmlFromPlain(
                    command.request().subject(),
                    plain,
                    command.signedPortalUrl().orElse(null)
            );
            helper.setText(plain, html);

            if (command.pdfBytes().isPresent()) {
                helper.addAttachment(
                        "Nanobase-Toplanti-Notu.pdf",
                        new ByteArrayResource(command.pdfBytes().get()),
                        "application/pdf"
                );
            }

            mailSender.send(message);
            ProviderMessage providerMessage = ProviderMessage.accepted(
                    providerType(),
                    "smtp-" + UUID.randomUUID(),
                    clock.get()
            ).markDelivered(clock.get());
            return SendResult.delivered(providerMessage);
        } catch (Exception ex) {
            return SendResult.failed("SMTP_SEND_FAILED", ex.getMessage());
        }
    }

    /**
     * Direct branded test send bypassing the delivery queue.
     */
    public SendResult sendBrandedTest(String to, MeetingNoteDocument document) {
        validateConfiguration();
        try {
            byte[] pdf = pdfRenderer.render(document);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, fromDisplayName);
            helper.setTo(to);
            helper.setSubject("Nanobase Toplantı Notu · " + document.meetingTitle());

            String plain = document.executiveSummary();
            helper.setText(plain, MeetingNoteBrandedTemplates.emailHtml(document));
            helper.addAttachment(
                    "Nanobase-Toplanti-Notu.pdf",
                    new ByteArrayResource(pdf),
                    "application/pdf"
            );
            mailSender.send(message);
            return SendResult.delivered(ProviderMessage.accepted(
                    providerType(),
                    "smtp-test-" + UUID.randomUUID(),
                    clock.get()
            ).markDelivered(clock.get()));
        } catch (Exception ex) {
            return SendResult.failed("SMTP_TEST_FAILED", ex.getMessage());
        }
    }
}
