package com.nanobaseai.actenora.transcript.application;

import com.nanobaseai.actenora.transcript.domain.TranscriptDomainException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Validates multipart VTT uploads: extension, MIME, magic bytes, size.
 * Never logs file content.
 */
public final class VttUploadValidator {

    public static final long DEFAULT_MAX_BYTES = 25L * 1024 * 1024;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("vtt");
    private static final Set<String> ALLOWED_MIME = Set.of(
            "text/vtt",
            "text/plain",
            "application/octet-stream");

    private final long maxBytes;

    public VttUploadValidator() {
        this(DEFAULT_MAX_BYTES);
    }

    public VttUploadValidator(long maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be > 0");
        }
        this.maxBytes = maxBytes;
    }

    public void validate(String originalFilename, String declaredMime, byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        validateSize(bytes.length);
        validateExtension(originalFilename);
        validateMime(declaredMime);
        validateMagicBytes(bytes);
    }

    public void validateSize(long size) {
        if (size <= 0) {
            throw new TranscriptDomainException("EMPTY_FILE", "VTT file is empty");
        }
        if (size > maxBytes) {
            throw new TranscriptDomainException(
                    "FILE_TOO_LARGE",
                    "VTT exceeds max size of " + maxBytes + " bytes");
        }
    }

    public void validateExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new TranscriptDomainException("INVALID_EXTENSION", "Filename required");
        }
        String name = originalFilename.trim();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            throw new TranscriptDomainException("INVALID_EXTENSION", "Expected .vtt extension");
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new TranscriptDomainException("INVALID_EXTENSION", "Expected .vtt extension");
        }
    }

    public void validateMime(String declaredMime) {
        if (declaredMime == null || declaredMime.isBlank()) {
            throw new TranscriptDomainException("INVALID_MIME", "MIME type required");
        }
        String mime = declaredMime.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_MIME.contains(mime)) {
            throw new TranscriptDomainException("INVALID_MIME", "Unsupported MIME type");
        }
    }

    public void validateMagicBytes(byte[] bytes) {
        String probe = new String(bytes, 0, Math.min(bytes.length, 64), StandardCharsets.UTF_8);
        if (probe.startsWith("\uFEFF")) {
            probe = probe.substring(1);
        }
        String leading = probe.stripLeading();
        if (!leading.toUpperCase(Locale.ROOT).startsWith("WEBVTT")) {
            throw new TranscriptDomainException("INVALID_MAGIC", "VTT magic bytes missing (WEBVTT)");
        }
    }

    public long maxBytes() {
        return maxBytes;
    }
}
