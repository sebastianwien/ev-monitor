package com.evmonitor.application;

import com.evmonitor.infrastructure.external.ChargingStationRegistryClient.Station;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bildet Betreibernamen aus dem Ladesaeulenregister auf unsere kanonischen Ladenetze ab.
 *
 * <p>Bewusst streng: eine falsche Zuordnung verfaelscht spaeter die Preissimulation.
 * Was nicht sicher zugeordnet werden kann, faellt raus statt geraten zu werden.
 */
class CpoRegistryMatcherTest {

    private CpoRegistryMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new CpoRegistryMatcher(
                List.of("Allego", "EnBW", "IONITY", "E.ON Drive", "Mer", "ALDI Süd", "Aral Pulse", "EWE Go"),
                Map.of(
                        "enbw mobility+ ag und co.kg", "EnBW",
                        "bp europa se", "Aral Pulse",
                        "mer germany gmbh", "Mer"
                ));
    }

    @Test
    void exakterTrefferAufDenBetreibernamen() {
        assertThat(matcher.match(new Station("Allego", null))).contains("Allego");
    }

    @Test
    void trefferIstUnabhaengigVonGrossSchreibung() {
        assertThat(matcher.match(new Station("ALDI SÜD", null))).contains("ALDI Süd");
    }

    @Test
    void rechtsformSuffixWirdEntfernt() {
        assertThat(matcher.match(new Station("Allego GmbH", null))).contains("Allego");
        assertThat(matcher.match(new Station("IONITY GmbH", null))).contains("IONITY");
        assertThat(matcher.match(new Station("EWE Go GmbH", null))).contains("EWE Go");
        assertThat(matcher.match(new Station("E.ON Drive GmbH", null))).contains("E.ON Drive");
    }

    /** Firmierungen, die sich nicht aus dem Namen ableiten lassen, brauchen die Alias-Tabelle. */
    @Test
    void aliasTabelleLoestFirmierungenAuf() {
        assertThat(matcher.match(new Station("BP Europa SE", null))).contains("Aral Pulse");
        assertThat(matcher.match(new Station("EnBW mobility+ AG und Co.KG", null))).contains("EnBW");
        assertThat(matcher.match(new Station("Mer Germany GmbH", null))).contains("Mer");
    }

    @Test
    void markeSchlaegtBetreiber() {
        assertThat(matcher.match(new Station("Irgendeine Betreiber GmbH", "IONITY"))).contains("IONITY");
    }

    /**
     * "EnBW ODR AG" ist ein eigenstaendiger regionaler Betreiber. Ihn auf "EnBW" zu ziehen
     * waere geraten und wuerde einen falschen Arbeitspreis in die Simulation tragen.
     */
    @Test
    void aehnlicheAberFremdeNamenWerdenNichtGeraten() {
        assertThat(matcher.match(new Station("EnBW ODR AG", null))).isEmpty();
        assertThat(matcher.match(new Station("Merdedes-Benz High Power Charging", null))).isEmpty();
    }

    @Test
    void unbekannteBetreiberFallenRaus() {
        assertThat(matcher.match(new Station("Maike Schaper", null))).isEmpty();
        assertThat(matcher.match(new Station("Union Investment Real", null))).isEmpty();
    }

    @Test
    void leereEingabeLiefertNichts() {
        assertThat(matcher.match(new Station(null, null))).isEmpty();
        assertThat(matcher.match(new Station("   ", ""))).isEmpty();
    }

    @Test
    void reduziertEineStationslisteAufKanonischeNamenOhneDoppelte() {
        List<String> result = matcher.matchAll(List.of(
                new Station("Allego GmbH", "Allego"),
                new Station("Allego GmbH", null),
                new Station("Maike Schaper", null),
                new Station("BP Europa SE", "Aral Pulse")));

        assertThat(result).containsExactlyInAnyOrder("Allego", "Aral Pulse");
    }

    /** Haeufigere Betreiber zuerst: der wahrscheinlichste Treffer steht oben im Select. */
    @Test
    void sortiertNachHaeufigkeitImUmkreis() {
        List<String> result = matcher.matchAll(List.of(
                new Station("Allego GmbH", null),
                new Station("IONITY GmbH", null),
                new Station("IONITY GmbH", null),
                new Station("IONITY GmbH", null),
                new Station("Allego GmbH", null),
                new Station("BP Europa SE", null)));

        assertThat(result).containsExactly("IONITY", "Allego", "Aral Pulse");
    }
}
