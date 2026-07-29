package com.nanobaseai.actenora.security.portal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.InferenceRequest;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.InferenceResponse;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ModelDescriptor;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ModelRuntimePort;
import com.nanobaseai.actenora.transcript.api.dto.TranscriptSegmentView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MeetingQuestionServiceTest {

    @Test
    void returnsOnlyValidatedTranscriptCitations() {
        UUID segmentId = UUID.randomUUID();
        AtomicReference<InferenceRequest> captured = new AtomicReference<>();
        ModelRuntimePort runtime = runtime(
                """
                        {"status":"ANSWERED","answer":"Validated answer","citations":["%s"]}
                        """.formatted(segmentId),
                captured
        );
        MeetingQuestionService service = service(runtime);

        var answer = service.answer(
                "Question",
                List.of(new TranscriptSegmentView(
                        segmentId,
                        "Speaker",
                        "Evidence text",
                        100,
                        200,
                        List.of()
                ))
        );

        assertEquals("ANSWERED", answer.status());
        assertEquals(List.of(segmentId.toString()), answer.citationSegmentIds());
        assertEquals(List.of(segmentId.toString()), captured.get().allowedEvidenceSegmentIds());
    }

    @Test
    void suppressesAnswerWhenModelCitesUnknownSegment() {
        ModelRuntimePort runtime = runtime(
                """
                        {"status":"ANSWERED","answer":"Unsupported","citations":["%s"]}
                        """.formatted(UUID.randomUUID()),
                new AtomicReference<>()
        );
        MeetingQuestionService service = service(runtime);

        var answer = service.answer(
                "Question",
                List.of(new TranscriptSegmentView(
                        UUID.randomUUID(),
                        "Speaker",
                        "Evidence text",
                        100,
                        200,
                        List.of()
                ))
        );

        assertEquals("INSUFFICIENT_EVIDENCE", answer.status());
        assertNull(answer.text());
        assertEquals(List.of(), answer.citationSegmentIds());
    }

    private static MeetingQuestionService service(ModelRuntimePort runtime) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory(Map.of("runtime", runtime));
        return new MeetingQuestionService(
                factory.getBeanProvider(ModelRuntimePort.class),
                new ObjectMapper(),
                new DefaultResourceLoader(),
                "classpath:/portal/prompts/meeting-question.v1.txt",
                "MEETING_QUESTION",
                "meeting-question.v1",
                "meeting-question.schema.v1",
                512,
                30
        );
    }

    private static ModelRuntimePort runtime(
            String rawResponse,
            AtomicReference<InferenceRequest> captured
    ) {
        return new ModelRuntimePort() {
            @Override
            public ModelDescriptor descriptor() {
                return new ModelDescriptor("test", "test", "test@1", 8192, 1024);
            }

            @Override
            public InferenceResponse infer(InferenceRequest request) {
                captured.set(request);
                return new InferenceResponse(rawResponse, 100, 20, 10, "test@1");
            }

            @Override
            public boolean healthy() {
                return true;
            }
        };
    }
}
