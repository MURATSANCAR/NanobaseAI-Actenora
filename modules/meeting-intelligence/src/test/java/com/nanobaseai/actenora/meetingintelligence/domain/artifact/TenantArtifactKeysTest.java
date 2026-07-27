package com.nanobaseai.actenora.meetingintelligence.domain.artifact;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantArtifactKeysTest {

    @Test
    void buildsCanonicalNoteAndExtractionKeys() {
        TenantId tenant = TenantId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        UUID occurrence = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID noteId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID runId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        String draft = TenantArtifactKeys.noteDraft(tenant, occurrence, noteId, 2);
        String approved = TenantArtifactKeys.noteApproved(tenant, occurrence, noteId, 2);
        String bundle = TenantArtifactKeys.extractionBundle(tenant, occurrence, runId);

        assertEquals(
                "tenants/11111111-1111-1111-1111-111111111111/meetings/22222222-2222-2222-2222-222222222222"
                        + "/notes/33333333-3333-3333-3333-333333333333/v2/draft.json",
                draft
        );
        assertTrue(approved.endsWith("/v2/approved.json"));
        assertTrue(bundle.contains("/extractions/" + runId + "/bundle.json"));
        TenantArtifactKeys.assertTenantOwnsKey(tenant, draft);
    }

    @Test
    void rejectsForeignTenantKey() {
        TenantId tenant = TenantId.of(UUID.randomUUID());
        assertThrows(
                IllegalArgumentException.class,
                () -> TenantArtifactKeys.assertTenantOwnsKey(tenant, "tenants/other/meetings/x")
        );
    }
}
