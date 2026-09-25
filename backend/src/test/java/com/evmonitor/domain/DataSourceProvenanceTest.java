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
 * Herkunft je Ladung wird aus {@code data_source} abgeleitet, nicht gespeichert (R2). Die Tabelle
 * steht hier einmal vollständig: wer eine Quelle ergänzt, muss sie hier einordnen.
 */
class DataSourceProvenanceTest {

    private static final String TABLE = """
            USER_LOGGED,         MANUAL,       MANUAL
            SPRITMONITOR_IMPORT, SPRITMONITOR, UPLOAD
            TESLA_IMPORT,        TESLA,        SYNC
            TESLA_FLEET_IMPORT,  TESLA,        SYNC
            TESLA_LIVE,          TESLA,        LIVE
            TESLA_MANUAL_IMPORT, TESLA,        UPLOAD
            WALLBOX_OCPP,        OCPP_WALLBOX, LIVE
            WALLBOX_GOE,         GOE,          SYNC
            API_UPLOAD,          PUBLIC_API,   UPLOAD
            TRONITY_IMPORT,      TRONITY,      UPLOAD
            SMARTCAR_LIVE,       SMARTCAR,     LIVE
            VWGROUP_LIVE,        VW_GROUP,     LIVE
            TESSIE,              TESSIE,       UPLOAD
            XPENG_IMPORT,        XPENG,        UPLOAD
            XPENG_LIVE,          XPENG,        SYNC
            EU_DATA_ACT_IMPORT,  VW_GROUP,     UPLOAD
            EU_DATA_ACT_SYNC,    VW_GROUP,     SYNC
            TESLA_INFERRED,      TESLA,        LIVE
            """;

    @ParameterizedTest(name = "{0} → {1} / {2}")
    @CsvSource(textBlock = TABLE)
    void providerAndChannel(DataSource source, DataProvider provider, DataChannel channel) {
        assertThat(source.provider()).isEqualTo(provider);
        assertThat(source.channel()).isEqualTo(channel);
    }

    @Test
    void tableCoversEveryDataSource() {
        Set<String> listed = Arrays.stream(TABLE.strip().split("\n"))
                .map(line -> line.split(",")[0].strip())
                .collect(Collectors.toSet());

        assertThat(listed).containsExactlyInAnyOrderElementsOf(
                EnumSet.allOf(DataSource.class).stream().map(Enum::name).toList());
    }
}
