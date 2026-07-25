package ai.nanobase.actenora.observability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredLogEventTest {

    @Test
    void renderIncludesServiceAndMessage() {
        String rendered = StructuredLogEvent.info("platform-backend", "boot").render();
        assertTrue(rendered.contains("platform-backend"));
        assertTrue(rendered.contains("boot"));
    }
}
