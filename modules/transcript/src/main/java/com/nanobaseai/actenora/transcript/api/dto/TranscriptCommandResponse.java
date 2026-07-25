package com.nanobaseai.actenora.transcript.api.dto;

import com.nanobaseai.actenora.transcript.domain.TranscriptStatus;

import java.util.UUID;

public record TranscriptCommandResponse(UUID transcriptId, TranscriptStatus status) {
}
