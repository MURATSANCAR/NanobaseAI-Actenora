package com.nanobaseai.actenora.transcript.application.port.in;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.port.storage.AuthorizedUrl;
import com.nanobaseai.actenora.transcript.api.TranscriptId;

import java.time.Duration;
import java.util.Objects;

public record AuthorizeTranscriptDownloadQuery(
        TenantId requesterTenantId,
        TranscriptId transcriptId,
        Duration ttl
) {
    public static final Duration MAX_TTL = Duration.ofSeconds(AuthorizedUrl.DEFAULT_MAX_TTL_SECONDS);

    public AuthorizeTranscriptDownloadQuery {
        Objects.requireNonNull(requesterTenantId, "requesterTenantId");
        Objects.requireNonNull(transcriptId, "transcriptId");
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        if (ttl.compareTo(MAX_TTL) > 0) {
            ttl = MAX_TTL;
        }
    }
}
