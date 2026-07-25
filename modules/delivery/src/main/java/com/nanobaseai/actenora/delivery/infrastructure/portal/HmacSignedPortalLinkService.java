package com.nanobaseai.actenora.delivery.infrastructure.portal;

import com.nanobaseai.actenora.delivery.application.port.SignedPortalLinkPort;
import com.nanobaseai.actenora.delivery.domain.SignedPortalLink;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * HMAC-SHA256 signed portal links with absolute expiry.
 */
public final class HmacSignedPortalLinkService implements SignedPortalLinkPort {

    private static final String HMAC = "HmacSHA256";

    private final byte[] secret;
    private final String portalBaseUrl;

    public HmacSignedPortalLinkService(String secret, String portalBaseUrl) {
        Objects.requireNonNull(secret, "secret");
        if (secret.length() < 16) {
            throw new IllegalArgumentException("portal link secret must be at least 16 characters");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.portalBaseUrl = Objects.requireNonNull(portalBaseUrl, "portalBaseUrl");
    }

    @Override
    public SignedPortalLink issue(
            TenantId tenantId,
            UUID noteVersionId,
            UUID deliveryRequestId,
            String recipientEmail,
            Duration ttl,
            Instant now
    ) {
        Instant expiresAt = now.plus(ttl);
        String token = sign(tenantId, noteVersionId, deliveryRequestId, recipientEmail, expiresAt);
        String fingerprint = sha256Hex(token).substring(0, 16);
        URI url = URI.create(portalBaseUrl
                + "/notes/" + noteVersionId
                + "?t=" + token
                + "&exp=" + expiresAt.getEpochSecond()
                + "&d=" + deliveryRequestId);
        return new SignedPortalLink(url, expiresAt, fingerprint);
    }

    @Override
    public boolean isValid(SignedPortalLink link, Instant now) {
        return link != null && !link.isExpired(now);
    }

    @Override
    public boolean verifyToken(
            TenantId tenantId,
            UUID noteVersionId,
            UUID deliveryRequestId,
            String recipientEmail,
            String rawToken,
            Instant expiresAt,
            Instant now
    ) {
        if (now.isAfter(expiresAt) || now.equals(expiresAt)) {
            return false;
        }
        String expected = sign(tenantId, noteVersionId, deliveryRequestId, recipientEmail, expiresAt);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                rawToken.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String sign(
            TenantId tenantId,
            UUID noteVersionId,
            UUID deliveryRequestId,
            String recipientEmail,
            Instant expiresAt
    ) {
        String payload = tenantId.value()
                + "|" + noteVersionId
                + "|" + deliveryRequestId
                + "|" + recipientEmail.toLowerCase()
                + "|" + expiresAt.getEpochSecond();
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("failed to sign portal link", e);
        }
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
