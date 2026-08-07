package com.nanobaseai.actenora.security.microsoftconnection;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Envelope encryption for the Graph client secret stored at rest.
 *
 * <p>Uses AES-GCM with a key supplied via {@code actenora.secrets.encryption-key} (base64,
 * 16/24/32 bytes). Ciphertext is encoded {@code gcm:v1:base64(iv || ciphertext+tag)}. When no
 * key is configured the cipher is {@link #available() unavailable}: {@link #encrypt(String)}
 * throws, and {@link #decrypt(String)} passes through any legacy plaintext unchanged.
 */
public final class SecretCipher {

    private static final String PREFIX = "gcm:v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(String base64Key) {
        this.key = (base64Key == null || base64Key.isBlank())
                ? null
                : new SecretKeySpec(Base64.getDecoder().decode(base64Key.trim()), "AES");
    }

    public boolean available() {
        return key != null;
    }

    public String encrypt(String plaintext) {
        if (key == null) {
            throw new IllegalStateException(
                    "Cannot encrypt secret: actenora.secrets.encryption-key is not configured");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Secret encryption failed", ex);
        }
    }

    /**
     * Decrypts a stored value. Values without the {@code gcm:v1:} prefix are treated as legacy
     * plaintext and returned unchanged (supports dev/in-memory rows written without a key).
     */
    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(PREFIX)) {
            return stored;
        }
        if (key == null) {
            throw new IllegalStateException(
                    "Cannot decrypt stored secret: actenora.secrets.encryption-key is not configured");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            byte[] ciphertext = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Secret decryption failed", ex);
        }
    }
}
