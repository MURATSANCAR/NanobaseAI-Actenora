package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meeting.infrastructure.tenancy.FixedTenantContext;
import com.nanobaseai.actenora.microsoftconnection.api.MicrosoftConnectionApi;
import com.nanobaseai.actenora.microsoftconnection.application.model.GraphChangeNotification;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;
import com.nanobaseai.actenora.tenant.api.TenantApi;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GraphChangeNotificationProcessorHardFailTest {

    @Test
    void unmappedTenantHardFails() {
        MicrosoftConnectionApi api = Mockito.mock(MicrosoftConnectionApi.class);
        MeetingApi meetingApi = Mockito.mock(MeetingApi.class);
        CalendarMeetingUpsertAdapter upsert =
                new CalendarMeetingUpsertAdapter(meetingApi, new FixedTenantContext(TenantId.random(), UUID.randomUUID()));
        TenantApi tenantApi = Mockito.mock(TenantApi.class);
        Mockito.when(tenantApi.findById(Mockito.any())).thenReturn(Optional.empty());
        Mockito.when(tenantApi.findByEntraTenantId(Mockito.anyString())).thenReturn(Optional.empty());
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        GraphChangeNotificationProcessor processor = new GraphChangeNotificationProcessor(
                api,
                upsert,
                tenantApi,
                factory.getBeanProvider(com.nanobaseai.actenora.sharedkernel.messaging.port.OutboxPublisher.class)
        );

        ActenoraException ex = assertThrows(
                ActenoraException.class,
                () -> processor.process(new GraphChangeNotification(
                        "n1",
                        "sub-1",
                        "updated",
                        "users/alice@contoso.com/events",
                        "evt-1",
                        "cs",
                        UUID.randomUUID().toString()
                ))
        );
        assertEquals("GRAPH_TENANT_UNMAPPED", ex.code());
        Mockito.verifyNoInteractions(api);
    }
}
