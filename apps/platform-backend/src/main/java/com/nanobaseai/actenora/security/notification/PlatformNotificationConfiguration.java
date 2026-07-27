package com.nanobaseai.actenora.security.notification;

import com.nanobaseai.actenora.aiprocessing.application.port.AiJobDeadNotifier;
import com.nanobaseai.actenora.identity.api.IdentityApi;
import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meetingintelligence.api.ledger.ContinuityLedgerApi;
import com.nanobaseai.actenora.meetingintelligence.application.port.ActionItemRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.NoteApprovalOpenedNotifier;
import com.nanobaseai.actenora.notification.api.NotificationApi;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
public class PlatformNotificationConfiguration {

    @Bean
    PlatformUserNotificationPublisher platformUserNotificationPublisher(
            NotificationApi notificationApi,
            IdentityApi identityApi,
            ObjectProvider<MeetingApi> meetingApi,
            ObjectProvider<ContinuityLedgerApi> ledgerApi,
            ObjectProvider<ActionItemRepository> actionItems
    ) {
        return new PlatformUserNotificationPublisher(
                notificationApi, identityApi, meetingApi, ledgerApi, actionItems
        );
    }

    @Bean
    NoteApprovalOpenedNotifier noteApprovalOpenedNotifier(PlatformUserNotificationPublisher publisher) {
        return publisher::notifyApprovalRequested;
    }

    @Bean
    AiJobDeadNotifier aiJobDeadNotifier(PlatformUserNotificationPublisher publisher) {
        return publisher::notifyAiJobFailed;
    }

    @Bean
    NotificationOverdueSweepScheduler notificationOverdueSweepScheduler(
            PlatformUserNotificationPublisher publisher,
            ObjectProvider<ContinuityLedgerApi> ledgerApi
    ) {
        return new NotificationOverdueSweepScheduler(publisher, ledgerApi);
    }

    public static final class NotificationOverdueSweepScheduler {
        private final PlatformUserNotificationPublisher publisher;
        private final ObjectProvider<ContinuityLedgerApi> ledgerApi;

        NotificationOverdueSweepScheduler(
                PlatformUserNotificationPublisher publisher,
                ObjectProvider<ContinuityLedgerApi> ledgerApi
        ) {
            this.publisher = publisher;
            this.ledgerApi = ledgerApi;
        }

        /**
         * Light sweep: when a ContinuityLedgerApi bean exists, overdue ensure is driven primarily
         * from portal list. This scheduler is a no-op placeholder reserved for multi-tenant
         * discovery; portal GET already calls {@link PlatformUserNotificationPublisher#ensureOverdueNotifications}.
         */
        @Scheduled(fixedDelayString = "${actenora.notifications.overdue-sweep-interval:PT15M}", initialDelayString = "PT2M")
        public void sweep() {
            ContinuityLedgerApi ledger = ledgerApi.getIfAvailable();
            if (ledger == null) {
                return;
            }
            // Tenant discovery is not exposed on TenantApi; overdue ensure runs on portal list.
        }

        public void sweepTenant(TenantId tenantId) {
            publisher.ensureOverdueNotifications(tenantId);
        }
    }
}
