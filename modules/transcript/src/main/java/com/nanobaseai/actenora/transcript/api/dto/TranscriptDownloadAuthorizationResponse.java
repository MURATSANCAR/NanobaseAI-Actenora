package com.nanobaseai.actenora.transcript.api.dto;

import java.net.URI;
import java.time.Instant;

public record TranscriptDownloadAuthorizationResponse(URI url, Instant expiresAt) {
}
