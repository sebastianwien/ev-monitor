package com.evmonitor.application.ingest;

import com.evmonitor.application.CoinLogService.CoinEvent;
import com.evmonitor.domain.DataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;

import static com.evmonitor.application.ingest.IngestDoor.CONNECTOR_PUSH;
import static com.evmonitor.application.ingest.IngestDoor.IMPORT_BATCH;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Die Policy-Tabelle bildet die gemessenen Regeln der beiden alten Türen ab (R2a, Verhalten
 * identisch). Fast alles hängt heute an der Tür; nur Dedup-Fenster, Tronity-Remap und Tesla-Coins
 * hängen an der Quelle.
 */
class IngestPoliciesTest {

    @ParameterizedTest
    @EnumSource(DataSource.class)
    void importBatch_bumpsIsolatesAndPaysApiCoins_withoutInheritanceOrSohEvent(DataSource source) {
        IngestPolicy p = IngestPolicies.forSource(source, IMPORT_BATCH);

        assertThat(p.bumpSameTimestampInBatch()).isTrue();
        assertThat(p.isolateEntryErrors()).isTrue();
        assertThat(p.coinEvent()).isEqualTo(CoinEvent.API_UPLOAD_LOG);
        assertThat(p.inheritTireAndRouteType()).isFalse();
        assertThat(p.sohEventWithoutVehicleKwh()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = DataSource.class, names = "EU_DATA_ACT_IMPORT", mode = EnumSource.Mode.EXCLUDE)
    void importBatch_dedupIsMinuteExact_exceptVwUpload(DataSource source) {
        assertThat(IngestPolicies.forSource(source, IMPORT_BATCH).dedupWindow()).isEqualTo(Duration.ZERO);
        assertThat(IngestPolicies.forSource(DataSource.EU_DATA_ACT_IMPORT, IMPORT_BATCH).dedupWindow())
                .isEqualTo(Duration.ofMinutes(3));
    }

    @ParameterizedTest
    @EnumSource(value = DataSource.class, names = "TRONITY_IMPORT", mode = EnumSource.Mode.EXCLUDE)
    void importBatch_onlyTronityReportsVehicleSideKwhInKwhField(DataSource source) {
        assertThat(IngestPolicies.forSource(source, IMPORT_BATCH).kwhIsVehicleSide()).isFalse();
        assertThat(IngestPolicies.forSource(DataSource.TRONITY_IMPORT, IMPORT_BATCH).kwhIsVehicleSide()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(DataSource.class)
    void connectorPush_inheritsAndFiresSoh_withoutBumpRemapOrWindow(DataSource source) {
        IngestPolicy p = IngestPolicies.forSource(source, CONNECTOR_PUSH);

        assertThat(p.inheritTireAndRouteType()).isTrue();
        assertThat(p.sohEventWithoutVehicleKwh()).isTrue();
        assertThat(p.isolateEntryErrors()).isFalse();
        assertThat(p.bumpSameTimestampInBatch()).isFalse();
        assertThat(p.kwhIsVehicleSide()).isFalse();
        assertThat(p.dedupWindow()).isEqualTo(Duration.ZERO);
    }

    @ParameterizedTest
    @EnumSource(value = DataSource.class, names = {"TESLA_LIVE", "TESLA_FLEET_IMPORT"}, mode = EnumSource.Mode.EXCLUDE)
    void connectorPush_paysCoinsOnlyForTesla(DataSource source) {
        assertThat(IngestPolicies.forSource(source, CONNECTOR_PUSH).coinEvent()).isNull();
        assertThat(IngestPolicies.forSource(DataSource.TESLA_LIVE, CONNECTOR_PUSH).coinEvent())
                .isEqualTo(CoinEvent.TESLA_DAILY_LOG);
        assertThat(IngestPolicies.forSource(DataSource.TESLA_FLEET_IMPORT, CONNECTOR_PUSH).coinEvent())
                .isEqualTo(CoinEvent.TESLA_DAILY_LOG);
    }
}
