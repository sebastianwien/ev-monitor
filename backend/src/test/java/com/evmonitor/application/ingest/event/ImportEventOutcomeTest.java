package com.evmonitor.application.ingest.event;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ImportEventOutcomeTest {

    @ParameterizedTest(name = "neu={0}, Fehler={1} -> {2}")
    @CsvSource({
            "3, 0, IMPORTED",
            "0, 0, NO_NEW_DATA",
            "2, 1, PARTIAL",
            "0, 2, FAILED",
    })
    void fromCounts(int imported, int failed, ImportEventOutcome expected) {
        assertThat(ImportEventOutcome.of(imported, failed)).isEqualTo(expected);
    }
}
