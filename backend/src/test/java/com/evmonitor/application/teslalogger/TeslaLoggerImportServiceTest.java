package com.evmonitor.application.teslalogger;

import com.evmonitor.domain.ChargingType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for TeslaLoggerImportService — date and location parsing.
 */
class TeslaLoggerImportServiceTest {

    private TeslaLoggerImportService service;

    @BeforeEach
    void setUp() {
        service = new TeslaLoggerImportService(mock(com.evmonitor.domain.EvLogRepository.class),
                mock(com.evmonitor.domain.CarRepository.class),
                mock(com.evmonitor.application.CoinLogService.class),
                new com.fasterxml.jackson.databind.ObjectMapper());
    }

    // ── parseDate ─────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "parseDate({0})")
    @CsvSource({
            "2025-08-20T10:56:48+02:00,     2025-08-20T08:56:48",   // offset → UTC
            "2025-08-20T10:56:48Z,          2025-08-20T10:56:48",   // Z suffix
            "2025-08-20T10:56:48,           2025-08-20T10:56:48",   // plain ISO
            "2025-08-20 10:56:48,           2025-08-20T10:56:48",   // space separator
            "2025-08-20 10:56,              2025-08-20T10:56:00",   // no seconds
            "20.08.2025 10:56:48,           2025-08-20T10:56:48",   // German
            "20.08.2025 10:56,              2025-08-20T10:56:00",   // German no seconds
            "08/20/2025 10:56:48,           2025-08-20T10:56:48",   // US
            "08/20/2025 10:56,              2025-08-20T10:56:00",   // US no seconds
            "2025-08-20,                    2025-08-20T00:00:00",   // date only ISO
            "20.08.2025,                    2025-08-20T00:00:00",   // date only German
            "08/20/2025,                    2025-08-20T00:00:00",   // date only US
    })
    void parseDate_recognizesFormat(String input, String expectedIso) {
        LocalDateTime result = service.parseDate(input.trim());
        assertNotNull(result, "Expected non-null for input: " + input.trim());
        assertEquals(LocalDateTime.parse(expectedIso.trim()), result);
    }

    @Test
    void parseDate_unixTimestampSeconds() {
        // 2025-08-20T10:56:48 UTC = 1755694608
        LocalDateTime result = service.parseDate("1755694608");
        assertNotNull(result);
        assertEquals(2025, result.getYear());
        assertEquals(8, result.getMonthValue());
        assertEquals(20, result.getDayOfMonth());
    }

    @Test
    void parseDate_unixTimestampMilliseconds() {
        // 1755694608000 ms
        LocalDateTime result = service.parseDate("1755694608000");
        assertNotNull(result);
        assertEquals(2025, result.getYear());
    }

    @Test
    void parseDate_null_returnsNull() {
        assertNull(service.parseDate(null));
    }

    @Test
    void parseDate_garbage_returnsNull() {
        assertNull(service.parseDate("not-a-date"));
    }

    // ── parseLocation ─────────────────────────────────────────────────────────

    @ParameterizedTest(name = "parseLocation({0}) → geohash")
    @CsvSource({
            "'48.2082,16.3738'",       // standard comma
            "'48.2082, 16.3738'",      // comma + space
            "48.2082;16.3738",         // semicolon
            "48.2082 16.3738",         // space only
            "'-33.8688,151.2093'",     // southern hemisphere (Sydney)
    })
    void parseLocation_latLon_returnsGeohash(String input) {
        String result = service.parseLocation(input);
        assertNotNull(result, "Expected geohash for: " + input);
        assertEquals(5, result.length(), "Geohash should be 5 chars (precision 5)");
    }

    @Test
    void parseLocation_vienna_isCorrectGeohash() {
        // 48.2082, 16.3738 = Vienna → geohash precision 5 starts with "u2e"
        String result = service.parseLocation("48.2082,16.3738");
        assertNotNull(result);
        assertTrue(result.startsWith("u2e"), "Vienna geohash should start with u2e, got: " + result);
    }

    @Test
    void parseLocation_placeName_returnsNull() {
        // Place names can't be geocoded without external API — geohash is null
        assertNull(service.parseLocation("Tesla Supercharger Wien"));
    }

    @Test
    void parseLocation_empty_returnsNull() {
        assertNull(service.parseLocation(null));
        assertNull(service.parseLocation(""));
        assertNull(service.parseLocation("  "));
    }

    // ── parseChargingType ─────────────────────────────────────────────────────

    @Test
    void parseChargingType_ac_returnsAC() {
        assertEquals(ChargingType.AC, service.parseChargingType("AC"));
    }

    @Test
    void parseChargingType_dc_returnsDC() {
        assertEquals(ChargingType.DC, service.parseChargingType("DC"));
    }

    @Test
    void parseChargingType_lowercase_isParsed() {
        assertEquals(ChargingType.AC, service.parseChargingType("ac"));
        assertEquals(ChargingType.DC, service.parseChargingType("dc"));
    }

    @Test
    void parseChargingType_null_returnsUnknown() {
        assertEquals(ChargingType.UNKNOWN, service.parseChargingType(null));
    }

    @Test
    void parseChargingType_garbage_returnsUnknown() {
        assertEquals(ChargingType.UNKNOWN, service.parseChargingType("Wechselstrom"));
    }
}
