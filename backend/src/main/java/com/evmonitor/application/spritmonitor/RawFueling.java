package com.evmonitor.application.spritmonitor;

/**
 * Pairing of the raw JSON string received from the SpritMonitor API with the parsed DTO.
 *
 * The raw JSON is stored verbatim in ev_log.raw_import_data so that unknown or
 * future SpritMonitor fields are never silently discarded. The DTO is used for
 * all import logic.
 */
public record RawFueling(SpritMonitorFuelingDTO dto, String rawJson) {}
