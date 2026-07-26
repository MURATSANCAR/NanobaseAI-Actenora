package com.nanobaseai.actenora.security.portal;

import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meeting.api.dto.CreateBusinessContextRequest;
import com.nanobaseai.actenora.meeting.api.dto.CreateMeetingRequest;
import com.nanobaseai.actenora.meeting.api.dto.CursorPageRequest;
import com.nanobaseai.actenora.meeting.api.dto.MeetingResponse;
import com.nanobaseai.actenora.meeting.domain.model.MeetingType;
import com.nanobaseai.actenora.meeting.domain.model.ProcessingPriority;
import com.nanobaseai.actenora.meeting.infrastructure.tenancy.FixedTenantContext;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.tenant.api.TenantApi;
import com.nanobaseai.actenora.tenant.api.TenantView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * FAZ 33 — local-only seed so the web portal can authenticate (mock Entra tid → tenant)
 * and see at least one meeting without Graph sync.
 */
@Component
@Order(100)
@ConditionalOnProperty(name = "actenora.portal.local-seed.enabled", havingValue = "true")
public class PortalLocalSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PortalLocalSeedRunner.class);

    private final TenantApi tenantApi;
    private final MeetingApi meetingApi;
    private final FixedTenantContext fixedTenantContext;
    private final String entraTenantId;
    private final String tenantName;

    public PortalLocalSeedRunner(
            TenantApi tenantApi,
            MeetingApi meetingApi,
            FixedTenantContext fixedTenantContext,
            @Value("${actenora.portal.local-seed.entra-tid:local-dev-tid}") String entraTenantId,
            @Value("${actenora.portal.local-seed.tenant-name:Actenora Local}") String tenantName
    ) {
        this.tenantApi = tenantApi;
        this.meetingApi = meetingApi;
        this.fixedTenantContext = fixedTenantContext;
        this.entraTenantId = entraTenantId;
        this.tenantName = tenantName;
    }

    @Override
    public void run(ApplicationArguments args) {
        TenantView tenant = tenantApi.findByEntraTenantId(entraTenantId)
                .orElseGet(() -> tenantApi.provision(
                        tenantName,
                        entraTenantId,
                        "UTC",
                        "en",
                        365
                ));
        TenantId tenantId = tenant.id();
        UUID seedActor = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        fixedTenantContext.use(tenantId, seedActor);

        if (!meetingApi.listMeetings(new CursorPageRequest(null, null, null, 1)).items().isEmpty()) {
            log.info("Portal local seed: tenant {} already has meetings — skipping demo meetings", tenantId.value());
            return;
        }

        var ctx = meetingApi.createBusinessContext(new CreateBusinessContextRequest(
                "PROJECT",
                "LOCAL-DEMO",
                "Local demo context",
                "Seeded for web-portal HTTP mode"
        ));

        Instant start = Instant.now().minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        MeetingResponse roadmap = meetingApi.createMeeting(new CreateMeetingRequest(
                ctx.id(),
                null,
                null,
                "seed-graph-event-roadmap",
                "seed-ical-roadmap",
                start,
                null,
                null,
                null,
                "Q3 roadmap sync",
                MeetingType.STANDALONE,
                start,
                start.plus(1, ChronoUnit.HOURS),
                ProcessingPriority.NORMAL,
                List.of(
                        new CreateMeetingRequest.ParticipantInput(
                                "local-oid-admin",
                                "Ada Admin",
                                "ada@actenora.local",
                                "ORGANIZER",
                                false
                        ),
                        new CreateMeetingRequest.ParticipantInput(
                                "local-oid-approver",
                                "Omar Approver",
                                "omar@actenora.local",
                                "REQUIRED",
                                false
                        )
                )
        ));

        Instant start2 = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        meetingApi.createMeeting(new CreateMeetingRequest(
                ctx.id(),
                null,
                null,
                "seed-graph-event-escalation",
                "seed-ical-escalation",
                start2,
                null,
                null,
                null,
                "Customer escalation review",
                MeetingType.STANDALONE,
                start2,
                start2.plus(45, ChronoUnit.MINUTES),
                ProcessingPriority.HIGH,
                List.of(
                        new CreateMeetingRequest.ParticipantInput(
                                "local-oid-member",
                                "Mia Member",
                                "mia@actenora.local",
                                "REQUIRED",
                                false
                        )
                )
        ));

        log.info(
                "Portal local seed ready: entraTid={} tenantId={} sampleMeetingId={}",
                entraTenantId,
                tenantId.value(),
                roadmap.id()
        );
    }
}
