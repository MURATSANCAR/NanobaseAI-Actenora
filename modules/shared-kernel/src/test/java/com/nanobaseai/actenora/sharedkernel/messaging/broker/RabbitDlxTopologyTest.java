package com.nanobaseai.actenora.sharedkernel.messaging.broker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RabbitDlxTopologyTest {

    @Test
    void namesAlignWithLocalInfrastructureDefinitions() {
        assertEquals("actenora.domain", RabbitDlxTopology.EVENTS_EXCHANGE);
        assertEquals("actenora.dlx", RabbitDlxTopology.DLX_EXCHANGE);
        assertEquals("actenora.dlq", RabbitDlxTopology.SHARED_DLQ);
        assertEquals("actenora.transcript.events", RabbitDlxTopology.consumerQueue("transcript"));
        assertEquals("actenora.dlq", RabbitDlxTopology.consumerDlq("transcript"));
        assertEquals("transcript.events", RabbitDlxTopology.deadLetterRoutingKey("transcript"));
    }
}
