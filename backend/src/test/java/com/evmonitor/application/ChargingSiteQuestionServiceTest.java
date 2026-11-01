package com.evmonitor.application;

import com.evmonitor.application.ingest.ChargingEntry;
import com.evmonitor.application.ingest.IngestCommand;
import com.evmonitor.application.ingest.IngestDoor;
import com.evmonitor.application.ingest.IngestGateway;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.ChargingSiteAttachment;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.User;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.domain.exception.ValidationException;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Bleibt bei einer Ladung mehr als eine Register-Säule offen, fragt die App den Nutzer einmal je
 * Ort und Ladeart. Seine Antwort ordnet alle offenen Ladungen dort zu, die technisch zur Säule passen.
 */
class ChargingSiteQuestionServiceTest extends AbstractIntegrationTest {

    private static final String CELL = "u0v9zq5";
    private static final NearbyStation DISCOUNTER = station("Discounter", 50, 22.0);
    private static final NearbyStation SUPERMARKT = station("Supermarkt", 60, 43.0);
    private static final NearbyStation PARKHAUS = station("Parkhaus", 70, 50.0);

    @Autowired private IngestGateway gateway;
    @Autowired private ChargingSiteQuestionService questions;
    @Autowired private ChargingSiteService chargingSiteService;

    private User user;
    private Car car;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("site-question-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de");
        car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        when(nearbyCpoService.findNearbyStations(anyString()))
                .thenReturn(Optional.of(List.of(DISCOUNTER, SUPERMARKT, PARKHAUS)));
    }

    // ── Frage anlegen ──

    @Test
    void mehrdeutigeLadungenAmSelbenOrtErgebenEineFrage() {
        ingest(ac(1, "11"));
        ingest(ac(2, "11"));

        var open = questions.openQuestions(user.getId());

        assertThat(open).hasSize(1);
        assertThat(open.get(0).logCount()).isEqualTo(2);
        assertThat(open.get(0).lastLoggedAt()).isEqualTo(LocalDateTime.of(2026, 10, 2, 20, 0));
        assertThat(open.get(0).chargingType()).isEqualTo(ChargingType.AC);
        assertThat(open.get(0).candidates()).extracting(NearbyStation::name)
                .containsExactly("Discounter", "Supermarkt", "Parkhaus");
    }

    @Test
    void eindeutigeLadungFragtNicht() {
        when(nearbyCpoService.findNearbyStations(anyString())).thenReturn(Optional.of(List.of(SUPERMARKT)));

        ingest(ac(1, "11"));

        assertThat(questions.openQuestions(user.getId())).isEmpty();
    }

    @Test
    void ohneSäuleImRegisterFragtNicht() {
        when(nearbyCpoService.findNearbyStations(anyString())).thenReturn(Optional.of(List.of()));

        ingest(ac(1, "11"));

        assertThat(questions.openQuestions(user.getId())).isEmpty();
    }

    @Test
    void schnappschussSpeichertKeinePosition() {
        var withPosition = new NearbyStation("Discounter", true, 50, 22.0, null, 2, CELL, 48.1, 11.5,
                null, null, null, null, null, List.of(), null, null, null, null);
        when(nearbyCpoService.findNearbyStations(anyString())).thenReturn(Optional.of(List.of(withPosition, SUPERMARKT)));

        ingest(ac(1, "11"));

        NearbyStation stored = questions.openQuestions(user.getId()).get(0).candidates().get(0);
        assertThat(stored.latitude()).isNull();
        assertThat(stored.longitude()).isNull();
    }

    // ── Antwort ──

    @Test
    void antwortOrdnetDieOffenenLadungenZuUndZahltEinmal() {
        EvLog first = ingest(ac(1, "11"));
        EvLog second = ingest(ac(2, "11"));
        UUID id = questions.openQuestions(user.getId()).get(0).id();

        var result = questions.answer(user.getId(), id, "supermarkt");

        assertThat(result.assigned()).isEqualTo(2);
        assertThat(result.watt()).isEqualTo(CoinLogService.CoinEvent.SITE_CONFIRMED.getDefaultAmount());
        assertThat(reload(first).getChargingSiteId()).isEqualTo(reload(second).getChargingSiteId()).isNotNull();
        assertThat(reload(first).getCpoName()).isEqualTo("Supermarkt");
        assertThat(questions.openQuestions(user.getId())).isEmpty();

        var again = questions.answer(user.getId(), id, "Parkhaus");
        assertThat(again.assigned()).isZero();
        assertThat(again.watt()).isZero();
        assertThat(reload(first).getCpoName()).isEqualTo("Supermarkt");
    }

    @Test
    void ladungDieTechnischNichtZurSäulePasstBleibtOffen() {
        EvLog slow = ingest(ac(1, "11"));
        // 40 kW schafft der Discounter (22 kW) nicht - gehört trotzdem zur selben Frage
        EvLog fast = ingest(ac(2, "40"));
        UUID id = questions.openQuestions(user.getId()).get(0).id();

        var result = questions.answer(user.getId(), id, "Discounter");

        assertThat(result.assigned()).isEqualTo(1);
        assertThat(reload(slow).getChargingSiteId()).isNotNull();
        assertThat(reload(fast).getChargingSiteId()).isNull();
    }

    @Test
    void vomNutzerGesetzteSäuleBleibtStehen() {
        EvLog log = ingest(ac(1, "11"));
        UUID id = questions.openQuestions(user.getId()).get(0).id();
        UUID chosen = chargingSiteService.siteFor(PARKHAUS).id();
        evLogRepository.attachChargingSite(new ChargingSiteAttachment(log.getId(), CELL, chosen, CELL,
                "Parkhaus", null, null, null));

        var result = questions.answer(user.getId(), id, "Supermarkt");

        assertThat(result.assigned()).isZero();
        assertThat(result.watt()).isZero();
        assertThat(reload(log).getChargingSiteId()).isEqualTo(chosen);
    }

    @Test
    void fremdeFrageGibtEsNicht() {
        ingest(ac(1, "11"));
        UUID id = questions.openQuestions(user.getId()).get(0).id();
        User other = createAndSaveUser("site-question-other-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de");

        assertThatThrownBy(() -> questions.answer(other.getId(), id, "Supermarkt")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> questions.dismiss(other.getId(), id)).isInstanceOf(NotFoundException.class);
        assertThat(questions.openQuestions(user.getId())).hasSize(1);
    }

    @Test
    void säuleAusserhalbDerFrageWirdAbgelehnt() {
        EvLog log = ingest(ac(1, "11"));
        UUID id = questions.openQuestions(user.getId()).get(0).id();

        assertThatThrownBy(() -> questions.answer(user.getId(), id, "Tankstelle"))
                .isInstanceOf(ValidationException.class);
        assertThat(reload(log).getChargingSiteId()).isNull();
    }

    // ── Weiß ich nicht ──

    @Test
    void weißNichtSchließtDieFrageUndFragtNichtErneut() {
        EvLog log = ingest(ac(1, "11"));
        UUID id = questions.openQuestions(user.getId()).get(0).id();

        questions.dismiss(user.getId(), id);
        ingest(ac(2, "11"));

        assertThat(questions.openQuestions(user.getId())).isEmpty();
        assertThat(reload(log).getChargingSiteId()).isNull();
    }

    private static NearbyStation station(String name, int distance, double acKw) {
        return new NearbyStation(name, true, distance, acKw, null, 2, CELL,
                null, null, null, "Hauptstraße", "1", "12345", "Beispielstadt", List.of("Typ 2"), null, null, null, null);
    }

    private static ChargingEntry ac(int day, String powerKw) {
        return ChargingEntry.builder().loggedAt(LocalDateTime.of(2026, 10, day, 20, 0)).geohash(CELL)
                .chargingType(ChargingType.AC).kwhCharged(new BigDecimal("20.0")).publicCharging(true)
                .maxChargingPowerKw(new BigDecimal(powerKw)).build();
    }

    private EvLog ingest(ChargingEntry entry) {
        var result = gateway.ingestCharging(new IngestCommand(user.getId(), car.getId(), DataSource.TESLA_LIVE,
                IngestDoor.CONNECTOR_PUSH, List.of(entry)));
        assertThat(result.imported()).isEqualTo(1);
        return result.created().get(0);
    }

    private EvLog reload(EvLog log) {
        return evLogRepository.findById(log.getId()).orElseThrow();
    }
}
