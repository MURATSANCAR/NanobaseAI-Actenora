package com.nanobaseai.actenora.microsoftconnection.infrastructure.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * Minimal RS256 JWT client assertion for Microsoft certificate credentials.
 */
public final class ClientAssertionJwtFactory {

    private ClientAssertionJwtFactory() {
    }

    public static String create(CertificateCredential credential, Instant now, DurationOrSeconds ttl) {
        Objects.requireNonNull(credential, "credential");
        Objects.requireNonNull(now, "now");
        long exp = now.getEpochSecond() + ttl.seconds();
        String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\",\"x5t\":\""
                + thumbprint(credential.certificate()) + "\"}");
        String payload = base64Url("{"
                + "\"aud\":\"" + escape(credential.tokenEndpoint()) + "\","
                + "\"iss\":\"" + escape(credential.clientId()) + "\","
                + "\"sub\":\"" + escape(credential.clientId()) + "\","
                + "\"jti\":\"" + UUID.randomUUID() + "\","
                + "\"nbf\":" + now.getEpochSecond() + ","
                + "\"exp\":" + exp
                + "}");
        String signingInput = header + "." + payload;
        String signature = base64Url(sign(signingInput.getBytes(StandardCharsets.US_ASCII), credential.privateKey()));
        return signingInput + "." + signature;
    }

    private static byte[] sign(byte[] input, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(input);
            return signature.sign();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign client assertion", ex);
        }
    }

    private static String thumbprint(X509Certificate certificate) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(certificate.getEncoded());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute certificate thumbprint", ex);
        }
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record DurationOrSeconds(long seconds) {
        public static DurationOrSeconds ofSeconds(long seconds) {
            return new DurationOrSeconds(seconds);
        }
    }
}
