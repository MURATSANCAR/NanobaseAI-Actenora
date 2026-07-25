package com.nanobaseai.actenora.sharedkernel.messaging.broker;

/**
 * RabbitMQ DLX/DLQ naming conventions (ADR-008). Topology is declared by the
 * platform adapter; this class keeps routing stable and broker-agnostic docs.
 *
 * <pre>
 *   exchange actenora.events
 *     → queue actenora.&lt;consumer&gt;.q
 *         dead-letter-exchange = actenora.events.dlx
 *         dead-letter-routing-key = actenora.&lt;consumer&gt;.dlq
 *   exchange actenora.events.dlx
 *     → queue actenora.&lt;consumer&gt;.dlq
 * </pre>
 */
public final class RabbitDlxTopology {

    public static final String EVENTS_EXCHANGE = "actenora.events";
    public static final String DLX_EXCHANGE = "actenora.events.dlx";
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
        return "actenora." + requireName(consumerName) + ".q";
    }

    public static String consumerDlq(String consumerName) {
        return "actenora." + requireName(consumerName) + ".dlq";
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
