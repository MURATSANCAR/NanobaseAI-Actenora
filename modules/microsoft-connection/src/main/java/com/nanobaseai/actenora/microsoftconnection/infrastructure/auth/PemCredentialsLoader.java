package com.nanobaseai.actenora.microsoftconnection.infrastructure.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;

/**
 * Loads X.509 certificate + PKCS#8 private key PEM files for certificate auth.
 */
public final class PemCredentialsLoader {

    private PemCredentialsLoader() {
    }

    public static Material load(String certificatePemPath, String privateKeyPemPath) {
        Objects.requireNonNull(certificatePemPath, "certificatePemPath");
        Objects.requireNonNull(privateKeyPemPath, "privateKeyPemPath");
        try {
            X509Certificate certificate = readCertificate(Files.readString(Path.of(certificatePemPath)));
            PrivateKey privateKey = readPrivateKey(Files.readString(Path.of(privateKeyPemPath)));
            return new Material(certificate, privateKey);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load certificate credentials from PEM", ex);
        }
    }

    public static Material loadFromPemStrings(String certificatePem, String privateKeyPem) {
        return new Material(readCertificate(certificatePem), readPrivateKey(privateKeyPem));
    }

    private static X509Certificate readCertificate(String pem) {
        try {
            byte[] der = decodePem(pem, "CERTIFICATE");
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(new java.io.ByteArrayInputStream(der));
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid certificate PEM", ex);
        }
    }

    private static PrivateKey readPrivateKey(String pem) {
        try {
            byte[] der = decodePem(pem, "PRIVATE KEY");
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return factory.generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid private key PEM (PKCS#8 required)", ex);
        }
    }

    private static byte[] decodePem(String pem, String type) {
        String normalized = pem.replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized.getBytes(StandardCharsets.US_ASCII));
    }

    public record Material(X509Certificate certificate, PrivateKey privateKey) {
        public Material {
            Objects.requireNonNull(certificate, "certificate");
            Objects.requireNonNull(privateKey, "privateKey");
        }
    }
}
