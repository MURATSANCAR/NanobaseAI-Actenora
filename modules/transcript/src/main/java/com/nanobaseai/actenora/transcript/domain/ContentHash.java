package com.nanobaseai.actenora.transcript.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * SHA-256 content fingerprint. Never log the underlying bytes.
 */
public record ContentHash(String sha256Hex) {

    public ContentHash {
        Objects.requireNonNull(sha256Hex, "sha256Hex");
        if (!sha256Hex.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256Hex must be 64 lowercase hex chars");
        }
    }

    public static ContentHash ofBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return new ContentHash(HexFormat.of().formatHex(hash));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static ContentHash ofUtf8(String text) {
        return ofBytes(text.getBytes(StandardCharsets.UTF_8));
    }
}
