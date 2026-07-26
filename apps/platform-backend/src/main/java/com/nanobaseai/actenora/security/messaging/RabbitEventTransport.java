package com.nanobaseai.actenora.security.messaging;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import com.nanobaseai.actenora.sharedkernel.messaging.broker.RabbitDlxTopology;
import com.nanobaseai.actenora.sharedkernel.messaging.port.EventTransport;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * RabbitMQ transport for outbox relay — publishes to {@link RabbitDlxTopology#EVENTS_EXCHANGE}
 * with routing key = event type.
 */
public final class RabbitEventTransport implements EventTransport {

    static final String HEADER_AGGREGATE_TYPE = "x-actenora-aggregate-type";
    static final String HEADER_AGGREGATE_ID = "x-actenora-aggregate-id";
    static final String HEADER_OCCURRED_AT = "x-actenora-occurred-at";
    static final String HEADER_PRODUCER = "x-actenora-producer";

    private final RabbitTemplate rabbitTemplate;

    public RabbitEventTransport(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate, "rabbitTemplate");
    }

    @Override
    public void publish(EventEnvelope envelope) throws TransportException {
        try {
            MessageProperties properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setContentEncoding(StandardCharsets.UTF_8.name());
            properties.setHeader(RabbitDlxTopology.HEADER_EVENT_ID, envelope.eventId().toString());
            properties.setHeader(RabbitDlxTopology.HEADER_EVENT_TYPE, envelope.eventType());
            properties.setHeader(RabbitDlxTopology.HEADER_EVENT_VERSION, envelope.eventVersion());
            properties.setHeader(RabbitDlxTopology.HEADER_TENANT_ID, envelope.tenantId().value().toString());
            properties.setHeader(RabbitDlxTopology.HEADER_CORRELATION_ID, envelope.correlationId().toString());
            envelope.optionalCausationId()
                    .ifPresent(id -> properties.setHeader(RabbitDlxTopology.HEADER_CAUSATION_ID, id.toString()));
            envelope.optionalTraceId()
                    .ifPresent(trace -> properties.setHeader(RabbitDlxTopology.HEADER_TRACE_ID, trace));
            properties.setHeader(HEADER_AGGREGATE_TYPE, envelope.aggregateType());
            properties.setHeader(HEADER_AGGREGATE_ID, envelope.aggregateId());
            properties.setHeader(HEADER_OCCURRED_AT, envelope.occurredAt().toString());
            envelope.optionalProducer()
                    .ifPresent(producer -> properties.setHeader(HEADER_PRODUCER, producer));

            byte[] body = envelope.payloadJson().getBytes(StandardCharsets.UTF_8);
            Message message = MessageBuilder.withBody(body).andProperties(properties).build();
            rabbitTemplate.send(
                    RabbitDlxTopology.EVENTS_EXCHANGE,
                    RabbitDlxTopology.routingKey(envelope.eventType()),
                    message
            );
        } catch (RuntimeException ex) {
            throw new TransportException("Failed to publish event " + envelope.eventId(), ex);
        }
    }

    static EventEnvelope toEnvelope(Message message) {
        MessageProperties properties = message.getMessageProperties();
        UUID eventId = UUID.fromString(requireHeader(properties, RabbitDlxTopology.HEADER_EVENT_ID));
        String eventType = requireHeader(properties, RabbitDlxTopology.HEADER_EVENT_TYPE);
        int eventVersion = ((Number) properties.getHeader(RabbitDlxTopology.HEADER_EVENT_VERSION)).intValue();
        Instant occurredAt = Instant.parse(requireHeader(properties, HEADER_OCCURRED_AT));
        TenantId tenantId = TenantId.of(UUID.fromString(requireHeader(properties, RabbitDlxTopology.HEADER_TENANT_ID)));
        String aggregateType = requireHeader(properties, HEADER_AGGREGATE_TYPE);
        String aggregateId = requireHeader(properties, HEADER_AGGREGATE_ID);
        UUID correlationId = UUID.fromString(requireHeader(properties, RabbitDlxTopology.HEADER_CORRELATION_ID));
        UUID causationId = optionalUuidHeader(properties, RabbitDlxTopology.HEADER_CAUSATION_ID);
        String traceId = optionalStringHeader(properties, RabbitDlxTopology.HEADER_TRACE_ID);
        String producer = optionalStringHeader(properties, HEADER_PRODUCER);
        String payloadJson = new String(message.getBody(), StandardCharsets.UTF_8);

        return new EventEnvelope(
                eventId,
                eventType,
                eventVersion,
                occurredAt,
                tenantId,
                aggregateType,
                aggregateId,
                correlationId,
                causationId,
                traceId,
                producer,
                payloadJson
        );
    }

    private static String requireHeader(MessageProperties properties, String name) {
        Object value = properties.getHeader(name);
        if (value == null) {
            throw new IllegalArgumentException("Missing header: " + name);
        }
        return value.toString();
    }

    private static String optionalStringHeader(MessageProperties properties, String name) {
        Object value = properties.getHeader(name);
        return value == null ? null : value.toString();
    }

    private static UUID optionalUuidHeader(MessageProperties properties, String name) {
        String value = optionalStringHeader(properties, name);
        return value == null ? null : UUID.fromString(value);
    }
}
