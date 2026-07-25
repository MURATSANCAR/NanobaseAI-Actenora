package ai.nanobase.actenora.meeting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MeetingModuleTest {

    @Test
    void moduleNameIsStable() {
        assertEquals("meeting", MeetingModule.name());
    }
}
