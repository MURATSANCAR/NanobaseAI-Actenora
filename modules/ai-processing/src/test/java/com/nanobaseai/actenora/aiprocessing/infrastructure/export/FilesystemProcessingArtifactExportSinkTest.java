package com.nanobaseai.actenora.aiprocessing.infrastructure.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingArtifact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilesystemProcessingArtifactExportSinkTest {

    @TempDir
    Path root;

    @Test
    void exportsQualityPackIdsAndLatestPointer() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID meetingId = UUID.randomUUID();
        UUID transcriptId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        String json = """
                {"artifactType":"quality-eval-pack","ids":{
                  "tenantId":"%s","meetingOccurrenceId":"%s","transcriptId":"%s",
                  "jobId":"%s","noteId":"%s"}}
                """.formatted(tenantId, meetingId, transcriptId, jobId, noteId);
        ProcessingArtifact artifact = ProcessingArtifact.inlineJson(
                tenantId, jobId, meetingId, "quality-eval-pack", json,
                Instant.parse("2026-08-06T10:00:00Z"));

        new FilesystemProcessingArtifactExportSink(root, new ObjectMapper()).export(artifact);

        Path meetingDir = root.resolve("tenants").resolve(tenantId.toString())
                .resolve("meetings").resolve(meetingId.toString());
        Path runDir = meetingDir.resolve("jobs").resolve(jobId.toString());
        assertTrue(Files.exists(runDir.resolve("quality-eval-pack.json")));
        var ids = new ObjectMapper().readTree(runDir.resolve("ids.json").toFile());
        assertEquals(transcriptId.toString(), ids.path("transcriptId").asText());
        assertEquals("jobs/" + jobId,
                new ObjectMapper().readTree(meetingDir.resolve("latest.json").toFile())
                        .path("relativePath").asText());
    }
}
