package ai.nanobase.actenora.meeting_intelligence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MeetingIntelligenceModuleTest {

    @Test
    void moduleNameIsStable() {
        assertEquals("meeting-intelligence", MeetingIntelligenceModule.name());
    }
}
