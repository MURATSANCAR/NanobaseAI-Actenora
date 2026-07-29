package com.nanobaseai.actenora.delivery.infrastructure.mail;

import com.nanobaseai.actenora.delivery.application.port.DeliveryMailProvider;
import com.nanobaseai.actenora.delivery.domain.DeliveryDomainException;
import com.nanobaseai.actenora.delivery.domain.DeliveryPolicySnapshot;
import com.nanobaseai.actenora.delivery.domain.ProviderMessage;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Microsoft Graph Mail.Send adapter. Authentication and HTTP remain owned by the
 * Microsoft Connection bounded context through the injected transport bridge.
 */
public final class MicrosoftGraphMailProvider implements DeliveryMailProvider {

    public record GraphMailConfig(
            String tenantId,
            String clientId,
            String senderUserPrincipalName,
            boolean enabled
    ) {
        public GraphMailConfig {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(clientId, "clientId");
            Objects.requireNonNull(senderUserPrincipalName, "senderUserPrincipalName");
        }
    }

    private final GraphMailConfig config;
    private final Supplier<Instant> clock;
    private final GraphMailTransport transport;
    private final AtomicBoolean sendImplemented = new AtomicBoolean(false);

    public MicrosoftGraphMailProvider(GraphMailConfig config, Supplier<Instant> clock) {
        this(config, clock, null);
    }

    public MicrosoftGraphMailProvider(
            GraphMailConfig config,
            Supplier<Instant> clock,
            GraphMailTransport transport
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.transport = transport;
    }

    /**
     * Test-only: simulate Graph acceptance once HTTP transport is wired.
     */
    public void enableSimulatedSend() {
        sendImplemented.set(true);
    }

    @Override
    public String providerType() {
        return DeliveryPolicySnapshot.PROVIDER_MICROSOFT_GRAPH;
    }

    @Override
    public void validateConfiguration() {
        if (!config.enabled()) {
            throw new DeliveryDomainException(
                    "PROVIDER_CONFIG_INVALID",
                    "Microsoft Graph mail provider is disabled");
        }
        if (config.tenantId().isBlank()
                || config.clientId().isBlank()
                || config.senderUserPrincipalName().isBlank()) {
            throw new DeliveryDomainException(
                    "PROVIDER_CONFIG_INVALID",
                    "Microsoft Graph mail configuration is incomplete");
        }
    }

    @Override
    public ProviderStatus getProviderStatus() {
        try {
            validateConfiguration();
            return new ProviderStatus(
                    true,
                    providerType(),
                    "graph mail port ready for " + config.senderUserPrincipalName());
        } catch (DeliveryDomainException ex) {
            return new ProviderStatus(false, providerType(), ex.getMessage());
        }
    }

    @Override
    public SendResult send(SendCommand command) {
        validateConfiguration();
        Instant now = clock.get();
        if (transport != null) {
            ProviderMessage message = transport.send(config, command, now);
            return SendResult.accepted(message);
        }
        if (!sendImplemented.get()) {
            return SendResult.failed(
                    "GRAPH_SEND_NOT_WIRED",
                    "Microsoft Graph Mail.Send adapter port is not yet connected to HTTP transport");
        }
        ProviderMessage message = ProviderMessage.accepted(
                providerType(),
                "graph-" + UUID.randomUUID(),
                now
        );
        return SendResult.accepted(message);
    }

    @FunctionalInterface
    public interface GraphMailTransport {
        ProviderMessage send(GraphMailConfig config, SendCommand command, Instant now);
    }
}
