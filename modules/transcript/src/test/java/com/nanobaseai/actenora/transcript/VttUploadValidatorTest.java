package com.nanobaseai.actenora.transcript;

import com.nanobaseai.actenora.transcript.application.VttUploadValidator;
import com.nanobaseai.actenora.transcript.domain.ContentHash;
import com.nanobaseai.actenora.transcript.domain.TranscriptDomainException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VttUploadValidatorTest {

    private final VttUploadValidator validator = new VttUploadValidator(1024);

    @Test
    void acceptsBomPrefixedWebvtt() {
        byte[] bytes = ("\uFEFFWEBVTT\n\n00:00:00.000 --> 00:00:01.000\nHi\n")
                .getBytes(StandardCharsets.UTF_8);
        validator.validate("cue.vtt", "text/vtt", bytes);
    }

    @Test
    void rejectsMissingExtension() {
        byte[] bytes = "WEBVTT\n".getBytes(StandardCharsets.UTF_8);
        TranscriptDomainException ex = assertThrows(
                TranscriptDomainException.class,
                () -> validator.validate("cue", "text/vtt", bytes));
        assertEquals("INVALID_EXTENSION", ex.code());
    }

    @Test
    void hashIsStable() {
        byte[] bytes = "WEBVTT\n".getBytes(StandardCharsets.UTF_8);
        assertEquals(ContentHash.ofBytes(bytes), ContentHash.ofBytes(bytes.clone()));
    }
}
