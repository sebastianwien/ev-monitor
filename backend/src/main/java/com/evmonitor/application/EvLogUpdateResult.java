package com.evmonitor.application;

/** Result of a partial log update plus the Watt earned by newly added data (price, card, CPO). */
public record EvLogUpdateResult(EvLogResponse log, int coinsAwarded) {
}
