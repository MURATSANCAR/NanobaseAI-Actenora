package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MeetingQualityPropertiesTest {

    @AfterEach
    void clear() {
        MeetingQualityProperties.clearInstall();
        System.clearProperty("actenora.meeting.quality.double-fallback-confidence-cap");
    }

    @Test
    void installOverridesEnvAndSysprop() {
        MeetingQualityProperties.install(new MeetingQualityProperties(0.6, 0.5, 0.4, true, 0.9, 0.65, 0.9));
        assertEquals(0.4d, MeetingQualityProperties.load().doubleFallbackConfidenceCap(), 0.0001);
    }

    @Test
    void syspropAliasIsHonoredWhenNotInstalled() {
        System.setProperty("actenora.meeting.quality.double-fallback-confidence-cap", "0.33");
        assertEquals(0.33d, MeetingQualityProperties.load().doubleFallbackConfidenceCap(), 0.0001);
    }
}
