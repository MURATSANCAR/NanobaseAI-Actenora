package com.nanobaseai.actenora.transcript.application;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import com.nanobaseai.actenora.transcript.domain.TranscriptDomainException;
import com.nanobaseai.actenora.transcript.domain.dictionary.DictionaryEntryKind;
import com.nanobaseai.actenora.transcript.domain.dictionary.TenantDictionary;
import com.nanobaseai.actenora.transcript.infrastructure.persistence.InMemoryTenantDictionaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantDictionaryApplicationServiceTest {

    private final TenantId tenantA = TenantId.random();
    private final TenantId tenantB = TenantId.random();
    private final InstantClock clock = new InstantClock(
            Clock.fixed(Instant.parse("2026-07-25T16:00:00Z"), ZoneOffset.UTC));

    private TenantDictionaryApplicationService service;

    @BeforeEach
    void setUp() {
        service = new TenantDictionaryApplicationService(new InMemoryTenantDictionaryRepository(), clock);
    }

    @Test
    void createAddEntryAndIsolateTenants() {
        TenantDictionary created = service.create(tenantA, "default");
        assertEquals(1L, created.revision());

        TenantDictionary updated = service.addEntry(
                tenantA,
                created.id(),
                DictionaryEntryKind.SPEAKER,
                "Ayşe Yılmaz",
                List.of("Ayşe"),
                null);
        assertEquals(2L, updated.revision());
        assertEquals(1, updated.entries().size());

        assertEquals(1, service.list(tenantA).size());
        assertTrue(service.list(tenantB).isEmpty());
        assertThrows(
                TranscriptDomainException.class,
                () -> service.get(tenantB, created.id()));
    }

    @Test
    void duplicateNameRejected() {
        service.create(tenantA, "default");
        TranscriptDomainException ex = assertThrows(
                TranscriptDomainException.class,
                () -> service.create(tenantA, "Default"));
        assertEquals("DICTIONARY_NAME_EXISTS", ex.code());
    }
}
