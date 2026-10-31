package com.evmonitor.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Welche Quelle zeigt Lücken im Kilometerstand als "ohne Aufzeichnung"? Die Tabelle steht hier
 * vollständig: wer eine Quelle ergänzt, muss sie bewusst ein- oder ausschalten.
 */
class DataSourceOdometerGapsTest {

    private static final String TABLE = """
            USER_LOGGED,          false
            SPRITMONITOR_IMPORT,  false
            TESLA_IMPORT,         false
            TESLA_FLEET_IMPORT,   false
            TESLA_LIVE,           true
            TESLA_MANUAL_IMPORT,  false
            WALLBOX_OCPP,         false
            WALLBOX_GOE,          false
            API_UPLOAD,           true
            TRONITY_IMPORT,       false
            SMARTCAR_LIVE,        false
            VWGROUP_LIVE,         false
            TESSIE,               true
            XPENG_IMPORT,         true
            XPENG_LIVE,           true
            EU_DATA_ACT_IMPORT,   false
            EU_DATA_ACT_SYNC,     false
            POLESTAR_DATA_PORTAL, true
            TESLA_INFERRED,       false
            """;

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(textBlock = TABLE)
    void showsOdometerGaps(DataSource source, boolean expected) {
        assertThat(source.showsOdometerGaps()).isEqualTo(expected);
    }

    @Test
    void tableCoversEveryDataSource() {
        Set<String> listed = Arrays.stream(TABLE.strip().split("\n"))
                .map(line -> line.split(",")[0].strip())
                .collect(Collectors.toSet());

        assertThat(listed).containsExactlyInAnyOrderElementsOf(
                EnumSet.allOf(DataSource.class).stream().map(Enum::name).toList());
    }

    @Test
    void tripSourceOutsideTheEnumCountsAsOff() {
        assertThat(EvTrip.showsOdometerGaps("TESSIE")).isTrue();
        assertThat(EvTrip.showsOdometerGaps("USER_CREATED")).isFalse();
        assertThat(EvTrip.showsOdometerGaps("SOMETHING_NEW")).isFalse();
        assertThat(EvTrip.showsOdometerGaps(null)).isFalse();
    }
}
