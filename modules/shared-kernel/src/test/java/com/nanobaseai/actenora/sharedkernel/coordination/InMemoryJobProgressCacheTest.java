package com.nanobaseai.actenora.sharedkernel.coordination;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryJobProgressCacheTest {

    @Test
    void storesLatestProgressPerMeeting() {
        JobProgressCache cache = new InMemoryJobProgressCache();
        UUID meetingId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-27T10:00:00Z");

        cache.put(meetingId, new JobProgressCache.Progress(jobId, "RUNNING", "running", 1, now));
        assertTrue(cache.get(meetingId).isPresent());
        assertEquals("RUNNING", cache.get(meetingId).orElseThrow().status());
        assertEquals("running", cache.get(meetingId).orElseThrow().stage());
    }
}
