package com.nanobaseai.actenora.observability.otel;

import com.nanobaseai.actenora.observability.CorrelationIds;
import com.nanobaseai.actenora.observability.PipelineStage;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineTracerTest {

    @Test
    void traceContinuityAcrossFullPipeline() {
        Instant t0 = Instant.parse("2026-07-25T10:00:00Z");
        PipelineTracer tracer = PipelineTracer.start(
                CorrelationIds.of("corr-pipeline-1")
                        .withJobId("job-9")
                        .withModelId("model-a")
                        .withDeploymentId("dep-1")
        );

        Instant cursor = t0;
        for (PipelineStage stage : PipelineStage.values()) {
            tracer.recordStage(stage, cursor, cursor.plusSeconds(1));
            cursor = cursor.plusSeconds(2);
        }

        assertTrue(tracer.hasContinuousTrace());
        assertTrue(tracer.isComplete());
        assertEquals(PipelineStage.values().length, tracer.spans().size());

        String traceId = tracer.traceId();
        for (PipelineSpan span : tracer.spans()) {
            assertEquals(traceId, span.traceId());
            assertTrue(span.isEnded());
            assertEquals("corr-pipeline-1", span.attributes().get("correlationId"));
            assertEquals("job-9", span.attributes().get("jobId"));
        }

        assertEquals("MeetingDiscovered", tracer.spans().getFirst().name());
        assertEquals("Delivered", tracer.spans().getLast().name());
    }

    @Test
    void rejectsOutOfOrderStages() {
        PipelineTracer tracer = PipelineTracer.startRoot("corr-2");
        Instant now = Instant.parse("2026-07-25T11:00:00Z");
        tracer.startStage(PipelineStage.MEETING_DISCOVERED, now);
        assertThrows(IllegalStateException.class,
                () -> tracer.startStage(PipelineStage.TRANSCRIPT_FETCHED, now.plusSeconds(1)));
    }
}
