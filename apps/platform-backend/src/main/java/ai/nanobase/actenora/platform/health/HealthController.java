package ai.nanobase.actenora.platform.health;

import ai.nanobase.actenora.approval.ApprovalModule;
import ai.nanobase.actenora.audit.AuditModule;
import ai.nanobase.actenora.delivery.DeliveryModule;
import ai.nanobase.actenora.identity.IdentityModule;
import ai.nanobase.actenora.meeting.MeetingModule;
import ai.nanobase.actenora.meeting_intelligence.MeetingIntelligenceModule;
import ai.nanobase.actenora.microsoft_connection.MicrosoftConnectionModule;
import ai.nanobase.actenora.model_management.ModelManagementModule;
import ai.nanobase.actenora.ai_processing.AiProcessingModule;
import ai.nanobase.actenora.observability.StructuredLogEvent;
import ai.nanobase.actenora.operations.OperationsModule;
import ai.nanobase.actenora.policy.PolicyModule;
import ai.nanobase.actenora.template.TemplateModule;
import ai.nanobase.actenora.tenant.TenantModule;
import ai.nanobase.actenora.transcript.TranscriptModule;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        System.out.println(StructuredLogEvent.info("platform-backend", "health-check").render());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "platform-backend");
        body.put("modules", List.of(
                IdentityModule.name(),
                TenantModule.name(),
                PolicyModule.name(),
                MicrosoftConnectionModule.name(),
                MeetingModule.name(),
                TranscriptModule.name(),
                ModelManagementModule.name(),
                AiProcessingModule.name(),
                MeetingIntelligenceModule.name(),
                ApprovalModule.name(),
                TemplateModule.name(),
                DeliveryModule.name(),
                AuditModule.name(),
                OperationsModule.name()
        ));
        return body;
    }
}
