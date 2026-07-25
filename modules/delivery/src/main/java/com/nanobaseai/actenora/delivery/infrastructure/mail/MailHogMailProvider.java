package com.nanobaseai.actenora.delivery.infrastructure.mail;

import com.nanobaseai.actenora.delivery.application.port.DeliveryMailProvider;
import com.nanobaseai.actenora.delivery.domain.DeliveryDomainException;
import com.nanobaseai.actenora.delivery.domain.DeliveryPolicySnapshot;
import com.nanobaseai.actenora.delivery.domain.ProviderMessage;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * MailHog / SMTP adapter for local FAZ 2 infra. Returns {@link SendOutcome#PROVIDER_ACCEPTED}
 * only — final {@code DELIVERED} requires a separate confirmation (acceptance ≠ delivery).
 */
public final class MailHogMailProvider implements DeliveryMailProvider {

    public record CapturedMail(
            String to,
            String subject,
            String body,
            boolean hasPdf,
            String portalUrl,
            Instant capturedAt
    ) {
    }

    private final String host;
    private final int port;
    private final String fromAddress;
    private final Supplier<Instant> clock;
    private final List<CapturedMail> sent = Collections.synchronizedList(new ArrayList<>());
    private final AtomicReference<Duration> artificialDelay = new AtomicReference<>(Duration.ZERO);
    private final AtomicReference<SendOutcome> forcedOutcome = new AtomicReference<>();
    private final AtomicReference<String> forcedFailureCode = new AtomicReference<>();
    private volatile boolean configurationValid = true;

    public MailHogMailProvider(String host, int port, String fromAddress, Supplier<Instant> clock) {
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.fromAddress = Objects.requireNonNull(fromAddress, "fromAddress");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static MailHogMailProvider localDefaults(Supplier<Instant> clock) {
        return new MailHogMailProvider("localhost", 1025, "noreply@actenora.local", clock);
    }

    public void setArtificialDelay(Duration delay) {
        artificialDelay.set(Objects.requireNonNull(delay, "delay"));
    }

    public void forceOutcome(SendOutcome outcome, String failureCode) {
        forcedOutcome.set(outcome);
        forcedFailureCode.set(failureCode);
    }

    public void clearForcedOutcome() {
        forcedOutcome.set(null);
        forcedFailureCode.set(null);
    }

    public void setConfigurationValid(boolean valid) {
        this.configurationValid = valid;
    }

    public List<CapturedMail> sentMails() {
        return List.copyOf(sent);
    }

    @Override
    public String providerType() {
        return DeliveryPolicySnapshot.PROVIDER_MAILHOG;
    }

    @Override
    public void validateConfiguration() {
        if (!configurationValid || host.isBlank() || port < 1) {
            throw new DeliveryDomainException(
                    "PROVIDER_CONFIG_INVALID",
                    "MailHog SMTP configuration is invalid");
        }
    }

    @Override
    public ProviderStatus getProviderStatus() {
        if (!configurationValid) {
            return new ProviderStatus(false, providerType(), "configuration invalid");
        }
        return new ProviderStatus(true, providerType(), "smtp://" + host + ":" + port);
    }

    @Override
    public SendResult send(SendCommand command) {
        validateConfiguration();
        Duration delay = artificialDelay.get();
        Duration timeout = command.timeout();
        if (delay.compareTo(timeout) > 0) {
            return SendResult.timeout("MailHog send exceeded timeout of " + timeout);
        }

        SendOutcome forced = forcedOutcome.get();
        if (forced != null) {
            return switch (forced) {
                case TIMEOUT -> SendResult.timeout("forced timeout");
                case DEFERRED -> SendResult.deferred(
                        forcedFailureCode.get() == null ? "TRANSIENT" : forcedFailureCode.get(),
                        "forced deferred");
                case BOUNCED -> SendResult.bounced(
                        forcedFailureCode.get() == null ? "BOUNCED" : forcedFailureCode.get(),
                        "forced bounce");
                case FAILED -> SendResult.failed(
                        forcedFailureCode.get() == null ? "PROVIDER_FAILED" : forcedFailureCode.get(),
                        "forced failure");
                case DELIVERED -> {
                    Instant now = clock.get();
                    ProviderMessage msg = ProviderMessage.accepted(
                            providerType(),
                            "mailhog-" + UUID.randomUUID(),
                            now
                    ).markDelivered(now);
                    yield SendResult.delivered(msg);
                }
                case PROVIDER_ACCEPTED -> accept(command);
            };
        }

        return accept(command);
    }

    private SendResult accept(SendCommand command) {
        Instant now = clock.get();
        sent.add(new CapturedMail(
                command.request().recipient().email(),
                command.request().subject(),
                command.request().bodyText(),
                command.pdfBytes().isPresent(),
                command.signedPortalUrl().orElse(null),
                now
        ));
        // One recipient per message — no BCC of externals together.
        ProviderMessage message = ProviderMessage.accepted(
                providerType(),
                "mailhog-" + UUID.randomUUID(),
                now
        );
        return SendResult.accepted(message);
    }
}
