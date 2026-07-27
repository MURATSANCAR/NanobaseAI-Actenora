package com.nanobaseai.actenora.sharedkernel.messaging.broker;

/**
 * RabbitMQ DLX/DLQ naming conventions (ADR-008), aligned with
 * {@code infrastructure/rabbitmq/definitions.json}.
 *
 * <pre>
 *   exchange actenora.domain
 *     → queue actenora.&lt;consumer&gt;.events
 *         dead-letter-exchange = actenora.dlx
 *         dead-letter-routing-key = &lt;consumer&gt;.events
 *   exchange actenora.dlx
 *     → queue actenora.dlq
 * </pre>
 */
public final class RabbitDlxTopology {

    public static final String EVENTS_EXCHANGE = "actenora.domain";
    public static final String PIPELINE_EXCHANGE = "actenora.ai.pipeline";
    public static final String DLX_EXCHANGE = "actenora.dlx";
    public static final String SHARED_DLQ = "actenora.dlq";
    public static final String HEADER_EVENT_ID = "x-actenora-event-id";
    public static final String HEADER_CORRELATION_ID = "x-actenora-correlation-id";
    public static final String HEADER_CAUSATION_ID = "x-actenora-causation-id";
    public static final String HEADER_TRACE_ID = "x-actenora-trace-id";
    public static final String HEADER_TENANT_ID = "x-actenora-tenant-id";
    public static final String HEADER_EVENT_TYPE = "x-actenora-event-type";
    public static final String HEADER_EVENT_VERSION = "x-actenora-event-version";

    private RabbitDlxTopology() {
    }

    public static String consumerQueue(String consumerName) {
        return "actenora." + requireName(consumerName) + ".events";
    }

    /** Shared DLQ queue name (local infra). Prefer {@link #SHARED_DLQ}. */
    public static String consumerDlq(String consumerName) {
        return SHARED_DLQ;
    }

    public static String deadLetterRoutingKey(String consumerName) {
        return requireName(consumerName) + ".events";
    }

    public static String routingKey(String eventType) {
        return requireName(eventType);
    }

    private static String requireName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return value.trim();
    }
}
