package com.nanobaseai.actenora.audit.infrastructure.config;

import com.nanobaseai.actenora.audit.api.AuditApi;
import com.nanobaseai.actenora.audit.application.AuditAppendService;
import com.nanobaseai.actenora.audit.application.port.AuditEntryStore;
import com.nanobaseai.actenora.audit.infrastructure.AuditApiAdapter;
import com.nanobaseai.actenora.audit.infrastructure.InMemoryAuditEntryStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuditModuleConfiguration {

    @Bean
    @ConditionalOnMissingBean(AuditEntryStore.class)
    AuditEntryStore auditEntryStore() {
        return new InMemoryAuditEntryStore();
    }

    @Bean
    @ConditionalOnMissingBean(AuditAppendService.class)
    AuditAppendService auditAppendService(AuditEntryStore auditEntryStore) {
        return new AuditAppendService(auditEntryStore);
    }

    @Bean
    @ConditionalOnMissingBean(AuditApi.class)
    AuditApi auditApi(AuditAppendService auditAppendService) {
        return new AuditApiAdapter(auditAppendService);
    }
}
