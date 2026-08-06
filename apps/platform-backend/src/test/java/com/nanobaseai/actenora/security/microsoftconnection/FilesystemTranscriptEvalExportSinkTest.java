package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class FilesystemTranscriptEvalExportSinkTest {

    @TempDir
    Path root;

    @Test
    void writesVttUnderUuidScopedMeetingPath() throws Exception {
        TenantId tenantId = TenantId.random();
        UUID meetingId = UUID.randomUUID();
        UUID transcriptId = UUID.randomUUID();
        byte[] vtt = "WEBVTT\n\n00:00.000 --> 00:01.000\nMerhaba".getBytes(StandardCharsets.UTF_8);

        new FilesystemTranscriptEvalExportSink(root).export(tenantId, meetingId, transcriptId, vtt);

        Path exported = root.resolve("tenants").resolve(tenantId.value().toString())
                .resolve("meetings").resolve(meetingId.toString())
                .resolve("transcripts").resolve(transcriptId.toString())
                .resolve("transcript.vtt");
        assertArrayEquals(vtt, Files.readAllBytes(exported));
    }
}
