package com.nanobaseai.actenora.aiprocessing.application.pipeline;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriorMeetingContextTest {

    @Test
    void rendersPromptBlockForCarryOvers() {
        PriorMeetingContext context = new PriorMeetingContext(
                Optional.of(UUID.randomUUID()),
                List.of("Finish migration plan"),
                List.of("Vendor delay"),
                List.of(),
                List.of("Keep freeze until Friday"),
                List.of()
        );
        String block = context.toPromptBlock();
        assertTrue(block.contains("OPEN_TASKS"));
        assertTrue(block.contains("Finish migration plan"));
        assertTrue(block.contains("ACTIVE_DECISIONS"));
        assertFalse(context.isEmpty());
    }

    @Test
    void emptyContextHasBlankPromptBlock() {
        assertTrue(PriorMeetingContext.EMPTY.toPromptBlock().isBlank());
        assertTrue(PriorMeetingContext.EMPTY.isEmpty());
    }
}
