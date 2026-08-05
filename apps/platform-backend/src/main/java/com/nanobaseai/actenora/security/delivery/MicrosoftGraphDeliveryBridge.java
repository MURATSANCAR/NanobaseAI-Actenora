package com.nanobaseai.actenora.security.delivery;

import com.nanobaseai.actenora.delivery.application.model.DraftMinutesReadyMailBody;
import com.nanobaseai.actenora.delivery.application.model.MeetingEndedMailBody;
import com.nanobaseai.actenora.delivery.application.port.DeliveryMailProvider;
import com.nanobaseai.actenora.delivery.domain.DeliveryIntent;
import com.nanobaseai.actenora.delivery.domain.DeliveryPolicySnapshot;
import com.nanobaseai.actenora.delivery.domain.ProviderMessage;
import com.nanobaseai.actenora.delivery.infrastructure.mail.MicrosoftGraphMailProvider;
import com.nanobaseai.actenora.delivery.infrastructure.render.MeetingNoteBrandedTemplates;
import com.nanobaseai.actenora.microsoftconnection.api.MicrosoftConnectionApi;
import com.nanobaseai.actenora.microsoftconnection.application.model.MailSendRequest;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.graph.GraphApiException;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Connects Delivery's provider contract to the hardened Microsoft Graph HTTP client.
 */
public final class MicrosoftGraphDeliveryBridge implements DeliveryMailProvider {

    private final MicrosoftGraphMailProvider delegate;

    public MicrosoftGraphDeliveryBridge(
            MicrosoftConnectionApi microsoft,
            InstantClock clock,
            String tenantId,
            String clientId,
            String senderUserPrincipalName
    ) {
        Objects.requireNonNull(microsoft, "microsoft");
        this.delegate = new MicrosoftGraphMailProvider(
                new MicrosoftGraphMailProvider.GraphMailConfig(
                        tenantId,
                        clientId,
                        senderUserPrincipalName,
                        true
                ),
                clock::now,
                (config, command, now) -> send(microsoft, config, command, now)
        );
    }

    @Override
    public SendResult send(SendCommand command) {
        try {
            return delegate.send(command);
        } catch (GraphApiException ex) {
            return ex.retryable()
                    ? SendResult.deferred(ex.code(), ex.getMessage())
                    : SendResult.failed(ex.code(), ex.getMessage());
        }
    }

    @Override
    public void validateConfiguration() {
        delegate.validateConfiguration();
    }

    @Override
    public ProviderStatus getProviderStatus() {
        return delegate.getProviderStatus();
    }

    @Override
    public String providerType() {
        return delegate.providerType();
    }

    private static ProviderMessage send(
            MicrosoftConnectionApi microsoft,
            MicrosoftGraphMailProvider.GraphMailConfig config,
            SendCommand command,
            Instant now
    ) {
        var request = command.request();
        String bodyHtml = renderHtml(
                request.intent(),
                request.subject(),
                request.bodyText(),
                command.signedPortalUrl().orElse(null)
        );
        var result = microsoft.sendMail(
                request.tenantId().value(),
                new MailSendRequest(
                        config.senderUserPrincipalName(),
                        request.subject(),
                        bodyHtml,
                        List.of(request.recipient().email()),
                        request.idempotencyKey()
                )
        );
        return ProviderMessage.accepted(
                DeliveryPolicySnapshot.PROVIDER_MICROSOFT_GRAPH,
                result.providerMessageId(),
                now
        );
    }

    private static String renderHtml(String intent, String subject, String bodyText, String portalUrl) {
        if (DeliveryIntent.MEETING_ENDED.equals(intent)) {
            return MeetingNoteBrandedTemplates.meetingEndedEmailHtml(MeetingEndedMailBody.decode(bodyText));
        }
        if (DeliveryIntent.DRAFT_ORGANIZER.equals(intent)) {
            return MeetingNoteBrandedTemplates.draftMinutesReadyEmailHtml(
                    DraftMinutesReadyMailBody.decode(bodyText));
        }
        return MeetingNoteBrandedTemplates.emailHtmlFromPlain(subject, bodyText, portalUrl);
    }
}
