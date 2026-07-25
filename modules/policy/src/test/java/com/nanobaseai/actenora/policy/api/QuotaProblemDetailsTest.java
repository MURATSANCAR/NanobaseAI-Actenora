package com.nanobaseai.actenora.policy.api;

import com.nanobaseai.actenora.policy.domain.QuotaDimension;
import com.nanobaseai.actenora.policy.domain.QuotaExceededException;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuotaProblemDetailsTest {

    @Test
    void serializesRfc7807Shape() {
        QuotaExceededException ex = new QuotaExceededException(
                TenantId.of(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
                QuotaDimension.FILE_SIZE_BYTES,
                100,
                100,
                1
        );

        QuotaProblemDetails problem = QuotaProblemDetails.from(ex, URI.create("/api/v1/files"));
        String json = problem.toJson();

        assertEquals(429, problem.status());
        assertEquals("Quota Exceeded", problem.title());
        assertTrue(json.contains("\"type\":\"https://actenora.nanobase.ai/problems/quota-exceeded\""));
        assertTrue(json.contains("\"status\":429"));
        assertTrue(json.contains("\"instance\":\"/api/v1/files\""));
        assertTrue(json.contains("\"quotaDimension\":\"FILE_SIZE_BYTES\""));
        assertTrue(json.contains("\"code\":\"QUOTA_EXCEEDED\""));
    }
}
