package com.nanobaseai.actenora.meeting.infrastructure.collaboration;

import com.nanobaseai.actenora.meeting.application.collaboration.port.MeetingAppTokenValidator;
import com.nanobaseai.actenora.meeting.domain.collaboration.InvalidMeetingAppTokenException;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HMAC-style opaque token validator for the Teams Meeting App.
 * Rejects requests that rely on untrusted Teams context without a backend token.
 *
 * <p>Token format (local/dev): {@code base64url(tenantId|userId|teamsMeetingId|tokenId|signature)}
 * where signature is {@code base64url(HMAC-ish shared secret + payload)}.
 */
public final class HmacMeetingAppTokenValidator implements MeetingAppTokenValidator {

    private final String sharedSecret;
    private final Map<String, ValidatedMeetingPrincipal> issued = new ConcurrentHashMap<>();

    public HmacMeetingAppTokenValidator(String sharedSecret) {
        this.sharedSecret = Objects.requireNonNull(sharedSecret, "sharedSecret");
    }

    public String issueToken(TenantId tenantId, UUID userId, String teamsMeetingId) {
        String tokenId = UUID.randomUUID().toString();
        String payload = tenantId.value() + "|" + userId + "|" + nullToEmpty(teamsMeetingId) + "|" + tokenId;
        String signature = sign(payload);
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((payload + "|" + signature).getBytes(StandardCharsets.UTF_8));
        ValidatedMeetingPrincipal principal = new ValidatedMeetingPrincipal(
                tenantId, userId, teamsMeetingId, tokenId
        );
        issued.put(token, principal);
        return token;
    }

    @Override
    public ValidatedMeetingPrincipal validate(String authorizationHeader, UntrustedTeamsContext teamsContext) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new InvalidMeetingAppTokenException("missing Authorization bearer token");
        }
        String token = authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader.substring("Bearer ".length()).trim()
                : authorizationHeader.trim();
        if (token.isBlank()) {
            throw new InvalidMeetingAppTokenException("empty bearer token");
        }

        ValidatedMeetingPrincipal fromIssued = issued.get(token);
        ValidatedMeetingPrincipal principal = fromIssued != null ? fromIssued : parseAndVerify(token);

        if (teamsContext != null) {
            if (teamsContext.claimedTenantId() != null
                    && !teamsContext.claimedTenantId().isBlank()
                    && !principal.tenantId().value().toString().equals(teamsContext.claimedTenantId())) {
                throw new InvalidMeetingAppTokenException("Teams tenant claim does not match token");
            }
            if (teamsContext.claimedUserId() != null
                    && !teamsContext.claimedUserId().isBlank()
                    && !principal.userId().toString().equals(teamsContext.claimedUserId())) {
                throw new InvalidMeetingAppTokenException("Teams user claim does not match token");
            }
            if (teamsContext.teamsMeetingId() != null
                    && principal.teamsMeetingId() != null
                    && !principal.teamsMeetingId().isBlank()
                    && !principal.teamsMeetingId().equals(teamsContext.teamsMeetingId())) {
                throw new InvalidMeetingAppTokenException("Teams meeting id does not match token");
            }
        }
        return principal;
    }

    private ValidatedMeetingPrincipal parseAndVerify(String token) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|");
            if (parts.length != 5) {
                throw new InvalidMeetingAppTokenException("malformed token");
            }
            String payload = parts[0] + "|" + parts[1] + "|" + parts[2] + "|" + parts[3];
            if (!sign(payload).equals(parts[4])) {
                throw new InvalidMeetingAppTokenException("bad signature");
            }
            return new ValidatedMeetingPrincipal(
                    TenantId.of(UUID.fromString(parts[0])),
                    UUID.fromString(parts[1]),
                    parts[2].isBlank() ? null : parts[2],
                    parts[3]
            );
        } catch (IllegalArgumentException ex) {
            throw new InvalidMeetingAppTokenException("unparseable token");
        }
    }

    private String sign(String payload) {
        int mix = Objects.hash(sharedSecret, payload);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Integer.toHexString(mix).getBytes(StandardCharsets.UTF_8));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
