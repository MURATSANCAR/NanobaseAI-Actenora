package com.nanobaseai.actenora.security.delivery;

import com.nanobaseai.actenora.delivery.application.model.MeetingNoteDocument;
import com.nanobaseai.actenora.delivery.application.port.DeliveryMailProvider;
import com.nanobaseai.actenora.delivery.infrastructure.mail.SmtpMailProvider;
import com.nanobaseai.actenora.identity.api.RequiresPermission;
import com.nanobaseai.actenora.identity.api.Permission;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * Operator test endpoint for branded meeting-note email + PDF via Hostinger SMTP.
 */
@RestController
@RequestMapping("/api/v1/delivery/test")
@ConditionalOnProperty(name = "actenora.delivery.mail.provider", havingValue = "smtp")
public class MeetingNoteTestMailController {

    private final DeliveryMailProvider mailProvider;

    public MeetingNoteTestMailController(DeliveryMailProvider mailProvider) {
        this.mailProvider = Objects.requireNonNull(mailProvider);
    }

    @PostMapping("/meeting-note")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RequiresPermission(Permission.TENANT_ADMINISTER)
    public TestSendResponse sendTest(@RequestBody TestSendBody body) {
        if (!(mailProvider instanceof SmtpMailProvider smtp)) {
            throw new ActenoraException(
                    "SMTP_NOT_CONFIGURED",
                    "Meeting note test mail requires actenora.delivery.mail.provider=smtp");
        }
        String to = body.to() == null || body.to().isBlank()
                ? "muratsancar@nanobase.ai"
                : body.to().trim();
        MeetingNoteDocument doc = body.useSample() != null && body.useSample()
                ? MeetingNoteDocument.sampleDemo()
                : new MeetingNoteDocument(
                        require(body.meetingTitle(), "meetingTitle"),
                        nullToEmpty(body.meetingDate()),
                        nullToEmpty(body.duration()),
                        nullToEmpty(body.organizer()),
                        body.participants() == null ? java.util.List.of() : body.participants(),
                        nullToEmpty(body.executiveSummary()),
                        body.decisions() == null ? java.util.List.of() : body.decisions(),
                        body.actions() == null ? java.util.List.of() : body.actions(),
                        "Actenora · NanobaseAI"
                );
        DeliveryMailProvider.SendResult result = smtp.sendBrandedTest(to, doc);
        if (result.outcome() != DeliveryMailProvider.SendOutcome.DELIVERED) {
            throw new ActenoraException(
                    result.failureCode() == null ? "SMTP_SEND_FAILED" : result.failureCode(),
                    result.failureDetail() == null ? "SMTP send failed" : result.failureDetail());
        }
        return new TestSendResponse(to, "delivered", doc.meetingTitle());
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ActenoraException("VALIDATION_ERROR", field + " is required when useSample=false");
        }
        return value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record TestSendBody(
            String to,
            Boolean useSample,
            String meetingTitle,
            String meetingDate,
            String duration,
            String organizer,
            java.util.List<String> participants,
            String executiveSummary,
            java.util.List<String> decisions,
            java.util.List<String> actions
    ) {
    }

    public record TestSendResponse(String to, String status, String meetingTitle) {
    }
}
