package com.nanobaseai.actenora.security.storage;

import com.nanobaseai.actenora.meetingintelligence.domain.artifact.ArtifactKind;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryArtifactMetadataStore;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectPutRequest;
import com.nanobaseai.actenora.transcript.infrastructure.storage.InMemoryObjectStorage;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataRecordingObjectStorageTest {

    @Test
    void registersTranscriptRawOnPut() {
        InMemoryArtifactMetadataStore store = new InMemoryArtifactMetadataStore();
        MetadataRecordingObjectStorage storage = new MetadataRecordingObjectStorage(
                new InMemoryObjectStorage(),
                store,
                Clock.fixed(Instant.parse("2026-07-27T10:00:00Z"), ZoneOffset.UTC)
        );
        TenantId tenant = TenantId.of(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        UUID occurrence = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID transcriptId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        String key = "tenants/" + tenant.value() + "/transcripts/" + occurrence + "/" + transcriptId + "/raw.vtt";

        storage.put(ObjectPutRequest.ofBytes(key, "WEBVTT".getBytes(), "text/vtt"));

        assertEquals(1, store.findByOccurrence(tenant, occurrence).size());
        assertEquals(ArtifactKind.TRANSCRIPT_RAW, store.findByOccurrence(tenant, occurrence).getFirst().artifactKind());
        assertTrue(store.findByKey(tenant, key).isPresent());
    }

    @Test
    void infersArtifactKinds() {
        assertEquals(ArtifactKind.NOTE_DRAFT, MetadataRecordingObjectStorage.inferKind(
                "tenants/t/meetings/m/notes/n/v1/draft.json"));
        assertEquals(ArtifactKind.NOTE_APPROVED, MetadataRecordingObjectStorage.inferKind(
                "tenants/t/meetings/m/notes/n/v1/approved.json"));
        assertEquals(ArtifactKind.EXTRACTION_BUNDLE, MetadataRecordingObjectStorage.inferKind(
                "tenants/t/meetings/m/extractions/r/bundle.json"));
        assertEquals(ArtifactKind.OTHER, MetadataRecordingObjectStorage.inferKind("tenants/t/misc.bin"));
    }
}
