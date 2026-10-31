package com.evmonitor.application;

import com.evmonitor.domain.ChargingType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Eine Ladung gehört zu einer Register-Säule, wenn das eindeutig ist: Technik passt, und unter
 * den Säulen im Umkreis bleibt genau eine uebrig - oder die Historie des Nutzers oder ein klarer
 * Abstand entscheidet. Sonst bleibt die Ladung offen, statt geraten zu werden.
 */
class ChargingSiteMatcherTest {

    private static final BigDecimal AC_11 = new BigDecimal("11.4");

    private static NearbyStation station(String name, int distance, Double maxAcKw, Double maxDcKw, String geohash) {
        return new NearbyStation(name, true, distance, maxAcKw, maxDcKw, 2, geohash,
                null, null, null, null, null, null, null, List.of(), null, null, null, null);
    }

    private static final NearbyStation SUPERMARKT = station("Supermarkt", 60, 43.0, 50.0, "u0v9zq4");
    private static final NearbyStation SUPERCHARGER = station("Tesla", 40, null, 250.0, "u0v9zq5");
    private static final NearbyStation ENBW = station("EnBW", 90, 22.0, null, "u0v9zq7");

    @Test
    void eineSäuleImUmkreisWirdZugeordnet() {
        var result = ChargingSiteMatcher.pick(ChargingType.AC, AC_11, List.of(SUPERMARKT), Set.of());

        assertThat(result.station()).isEqualTo(SUPERMARKT);
    }

    @Test
    void säuleWeiterAls150mZähltNicht() {
        var far = station("Supermarkt", 180, 43.0, 50.0, "u0v9zq4");

        var result = ChargingSiteMatcher.pick(ChargingType.AC, AC_11, List.of(far), Set.of());

        assertThat(result.station()).isNull();
        assertThat(result.candidates()).isEmpty();
    }

    @Test
    void acLadungSchließtReineDcSäuleAus() {
        var result = ChargingSiteMatcher.pick(ChargingType.AC, AC_11, List.of(SUPERCHARGER, SUPERMARKT), Set.of());

        assertThat(result.station()).isEqualTo(SUPERMARKT);
    }

    @Test
    void dcLadungSchließtReineAcSäuleAus() {
        var result = ChargingSiteMatcher.pick(ChargingType.DC, new BigDecimal("45"), List.of(SUPERMARKT, ENBW), Set.of());

        assertThat(result.station()).isEqualTo(SUPERMARKT);
    }

    @Test
    void leistungÜberDerSäuleSchließtSieAus() {
        // 65 kW an einer 50-kW-Säule geht nicht - das war der Supercharger nebenan
        var result = ChargingSiteMatcher.pick(ChargingType.DC, new BigDecimal("65.4"), List.of(SUPERCHARGER, SUPERMARKT), Set.of());

        assertThat(result.station()).isEqualTo(SUPERCHARGER);
    }

    @Test
    void leistungKnappÜberDemRegisterwertBleibtImToleranzbereich() {
        // Fahrzeuge melden 11,4 kW an einer 11-kW-Säule
        var wallbox = station("Stadtwerke", 30, 11.0, null, "u0v9zq4");

        var result = ChargingSiteMatcher.pick(ChargingType.AC, AC_11, List.of(wallbox), Set.of());

        assertThat(result.station()).isEqualTo(wallbox);
    }

    @Test
    void säuleOhneLeistungsangabeBleibtKandidat() {
        var unknown = station("Supermarkt", 50, null, null, "u0v9zq4");

        var result = ChargingSiteMatcher.pick(ChargingType.AC, AC_11, List.of(unknown), Set.of());

        assertThat(result.station()).isEqualTo(unknown);
    }

    @Test
    void zweiKandidaten_dieHistorieDesNutzersEntscheidet() {
        var discounter = station("Discounter", 50, 22.0, null, "u0v9zq5");

        var result = ChargingSiteMatcher.pick(ChargingType.AC, AC_11, List.of(discounter, SUPERMARKT),
                Set.of(ChargingSiteMatcher.siteKey("u0v9zq4", "supermarkt")));

        assertThat(result.station()).isEqualTo(SUPERMARKT);
    }

    @Test
    void zweiKandidaten_deutlichNäherGewinnt() {
        var near = station("Discounter", 25, 22.0, null, "u0v9zq5");

        var result = ChargingSiteMatcher.pick(ChargingType.AC, AC_11, List.of(near, SUPERMARKT), Set.of());

        assertThat(result.station()).isEqualTo(near);
    }

    @Test
    void zweiKandidaten_ähnlichWeit_bleibtOffenMitBeidenKandidaten() {
        var discounter = station("Discounter", 40, 22.0, null, "u0v9zq5");

        var result = ChargingSiteMatcher.pick(ChargingType.AC, AC_11, List.of(discounter, SUPERMARKT), Set.of());

        assertThat(result.station()).isNull();
        assertThat(result.candidates()).containsExactly(discounter, SUPERMARKT);
    }

    @Test
    void ohneLadeartUndLeistungEntscheidetNurDerUmkreis() {
        var result = ChargingSiteMatcher.pick(null, null, List.of(SUPERCHARGER), Set.of());

        assertThat(result.station()).isEqualTo(SUPERCHARGER);
    }
}
