package com.nanobaseai.actenora.aiprocessing.infrastructure.adapter;

import com.nanobaseai.actenora.aiprocessing.application.pipeline.InferenceRequest;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.InferenceResponse;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ModelDescriptor;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ModelRuntimePort;
import com.nanobaseai.actenora.aiprocessing.domain.routing.InferenceTaskType;
import com.nanobaseai.actenora.aiprocessing.domain.routing.TenantRoutingPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoleAwareModelRuntimePortTest {

    @Test
    void routesExtractToFastAndFinalToLarge() {
        AtomicReference<String> used = new AtomicReference<>();
        ModelRuntimePort fast = stub("fast", used);
        ModelRuntimePort fin = stub("final", used);
        RoleAwareModelRuntimePort port = new RoleAwareModelRuntimePort(
                fast,
                fin,
                () -> TenantRoutingPolicy.defaults(UUID.randomUUID())
        );

        port.infer(req(InferenceTaskType.CHUNK_EXTRACTION));
        assertEquals("fast", used.get());

        port.infer(req(InferenceTaskType.MEETING_TRIAGE));
        assertEquals("fast", used.get());

        port.infer(req(InferenceTaskType.CANDIDATE_MERGE));
        assertEquals("final", used.get());

        port.infer(req(InferenceTaskType.FINAL_NOTE));
        assertEquals("final", used.get());
    }

    private static InferenceRequest req(InferenceTaskType type) {
        return new InferenceRequest(
                type.name(), "pv", "schema", "system", "user", List.of(), 100, 30);
    }

    private static ModelRuntimePort stub(String name, AtomicReference<String> used) {
        return new ModelRuntimePort() {
            @Override
            public ModelDescriptor descriptor() {
                return new ModelDescriptor(name, name, name + "@1", 4096, 1024);
            }

            @Override
            public InferenceResponse infer(InferenceRequest request) {
                used.set(name);
                return new InferenceResponse("{}", 1, 1, 1L, name + "@1");
            }

            @Override
            public boolean healthy() {
                return true;
            }
        };
    }
}
