package com.evmonitor.application;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvLogSavedEventTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 11, 7, 14);

    @Test
    void lookupAtIsMidpointOfChargingWindow() {
        EvLogSavedEvent event = EvLogSavedEvent.of(UUID.randomUUID(), "u3345", START, 292, null);
        assertEquals(START.plusMinutes(146), event.lookupAt());
    }

    @Test
    void lookupAtFallsBackToStartWithoutDuration() {
        assertEquals(START, EvLogSavedEvent.of(UUID.randomUUID(), "u3345", START, null, null).lookupAt());
        assertEquals(START, EvLogSavedEvent.of(UUID.randomUUID(), "u3345", START, 0, null).lookupAt());
    }
}
