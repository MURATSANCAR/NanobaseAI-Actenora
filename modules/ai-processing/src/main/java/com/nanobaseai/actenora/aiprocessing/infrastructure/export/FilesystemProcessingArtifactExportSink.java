package com.nanobaseai.actenora.aiprocessing.infrastructure.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobaseai.actenora.aiprocessing.application.port.ProcessingArtifactExportSink;
import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingArtifact;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.note.QualityEvalPack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Objects;
import java.util.Set;

/** Atomically exports successful quality packs and machine-readable id manifests. */
public final class FilesystemProcessingArtifactExportSink implements ProcessingArtifactExportSink {

    private final Path root;
    private final ObjectMapper mapper;

    public FilesystemProcessingArtifactExportSink(Path root, ObjectMapper mapper) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public void export(ProcessingArtifact artifact) {
        if (!QualityEvalPack.ARTIFACT_TYPE.equals(artifact.artifactType())) {
            return;
        }
        String payload = artifact.payloadJson().orElse(null);
        if (payload == null) {
            return;
        }
        try {
            JsonNode pack = mapper.readTree(payload);
            Path meetingDir = root.resolve("tenants").resolve(artifact.tenantId().toString())
                    .resolve("meetings").resolve(artifact.meetingOccurrenceId().toString());
            Path runDir = meetingDir.resolve("jobs").resolve(artifact.jobId().toString());
            writeAtomic(runDir.resolve("quality-eval-pack.json"),
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(pack));

            JsonNode sourceIds = pack.path("ids");
            ObjectNode ids = mapper.createObjectNode();
            ids.put("tenantId", sourceIds.path("tenantId").asText(artifact.tenantId().toString()));
            ids.put("meetingOccurrenceId", sourceIds.path("meetingOccurrenceId")
                    .asText(artifact.meetingOccurrenceId().toString()));
            ids.put("transcriptId", sourceIds.path("transcriptId").asText(""));
            ids.put("jobId", sourceIds.path("jobId").asText(artifact.jobId().toString()));
            ids.put("noteId", sourceIds.path("noteId").asText(""));
            writeAtomic(runDir.resolve("ids.json"),
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(ids));

            ObjectNode latest = mapper.createObjectNode();
            latest.put("jobId", artifact.jobId().toString());
            latest.put("relativePath", "jobs/" + artifact.jobId());
            latest.put("createdAt", artifact.createdAt().toString());
            writeAtomic(meetingDir.resolve("latest.json"),
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(latest));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to export quality evaluation artifact", ex);
        }
    }

    private static void writeAtomic(Path target, String content) throws IOException {
        Files.createDirectories(target.getParent());
        restrictOwnerOnly(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, content + System.lineSeparator(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            restrictOwnerOnly(target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void restrictOwnerOnly(Path path) {
        try {
            Set<PosixFilePermission> perms = Files.isDirectory(path)
                    ? PosixFilePermissions.fromString("rwx------")
                    : PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Non-POSIX filesystems (e.g. some CI mounts) skip ACL hardening.
        }
    }
}
