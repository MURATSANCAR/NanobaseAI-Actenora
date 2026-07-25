package com.nanobaseai.actenora;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Platform health endpoint (application bootstrap package — not a bounded context).
 */
@RestController
public class HealthController {

    private final String transcriptMode;

    public HealthController(
            @Value("${actenora.transcript.mode:embedded}") String transcriptMode) {
        this.transcriptMode = transcriptMode;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "platform-backend");
        body.put("transcriptMode", transcriptMode);
        body.put("modules", List.of(
                "identity",
                "tenant",
                "policy",
                "microsoft-connection",
                "meeting",
                "transcript",
                "model-management",
                "ai-processing",
                "meeting-intelligence",
                "approval",
                "template",
                "delivery",
                "audit",
                "operations"
        ));
        return body;
    }
}
