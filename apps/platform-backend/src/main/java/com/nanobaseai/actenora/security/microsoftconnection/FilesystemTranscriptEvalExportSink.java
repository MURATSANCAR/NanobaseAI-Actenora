package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Atomically exports immutable Graph VTT input beside production quality-eval artifacts. */
final class FilesystemTranscriptEvalExportSink implements TranscriptEvalExportSink {

    private final Path root;

    FilesystemTranscriptEvalExportSink(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    @Override
    public void export(TenantId tenantId, UUID meetingOccurrenceId, UUID transcriptId, byte[] vtt) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        Objects.requireNonNull(transcriptId, "transcriptId");
        Objects.requireNonNull(vtt, "vtt");
        Path target = root.resolve("tenants").resolve(tenantId.value().toString())
                .resolve("meetings").resolve(meetingOccurrenceId.toString())
                .resolve("transcripts").resolve(transcriptId.toString())
                .resolve("transcript.vtt");
        try {
            Files.createDirectories(target.getParent());
            restrictOwnerOnly(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), "transcript", ".tmp");
            try {
                Files.write(temporary, vtt);
                try {
                    Files.move(temporary, target,
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
                restrictOwnerOnly(target);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to export transcript evaluation artifact", ex);
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
