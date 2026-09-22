package com.evmonitor.application;

import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.DataSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ladegruppen der oeffentlichen Fahrzeugseite. Muss dieselben Eintraege
 * liefern wie das Dashboard (useLogList.ts): Ueberschussladen ohne Odometer
 * am selben Tag, danach Nachladungen mit identischem Odometer.
 */
class PublicChargeGrouperTest {

    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 9, 10, 8, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void goeMicroSessionsOhneOdometerWerdenProTagEineZeile() {
        List<EvLogResponse> logs = List.of(
                log(T0.plusDays(1), null, DataSource.WALLBOX_GOE, "0.8", "0.20", 20, "3.5"),
                log(T0.plusDays(1).minusHours(2), null, DataSource.WALLBOX_GOE, "1.2", "0.30", 40, "7.0"),
                log(T0, null, DataSource.WALLBOX_GOE, "0.5", "0.10", 10, "2.0"),
                log(T0.minusHours(3), null, DataSource.WALLBOX_GOE, "0.5", "0.10", 10, "2.0"));

        List<PublicCarResponse.Charge> out = PublicChargeGrouper.group(logs, 10);

        assertEquals(2, out.size());
        PublicCarResponse.Charge day2 = out.get(0);
        assertEquals(T0.plusDays(1).toLocalDate(), day2.chargedOn());
        assertEquals(2, day2.sessions());
        assertEquals(0, new BigDecimal("2.0").compareTo(day2.kwhCharged()));
        assertEquals(0, new BigDecimal("0.50").compareTo(day2.costEur()));
        assertEquals(60, day2.durationMinutes());
        assertEquals(0, new BigDecimal("7.0").compareTo(day2.maxChargingPowerKw()), "Spitze ist das Maximum");
    }

    @Test
    void einzelnesGoeLogAmTagBleibtEinzeln() {
        List<EvLogResponse> logs = List.of(log(T0, null, DataSource.WALLBOX_GOE, "5.0", null, 60, "11"));
        List<PublicCarResponse.Charge> out = PublicChargeGrouper.group(logs, 10);
        assertEquals(1, out.size());
        assertEquals(1, out.get(0).sessions());
    }

    @Test
    void aufeinanderfolgendeLogsMitGleichemOdometerWerdenEineZeile() {
        // API-Ueberschussladen: jeder Micro-Vorgang traegt denselben Odometer, das Auto steht.
        List<EvLogResponse> logs = List.of(
                log(T0.plusDays(3), 20_400, DataSource.TESLA_LIVE, "30", "12", 40, "150"),   // DC unterwegs
                log(T0.plusDays(2), 20_000, DataSource.TESLA_LIVE, "1.0", "0.3", 15, "4"),
                log(T0.plusDays(1), 20_000, DataSource.TESLA_LIVE, "1.5", "0.4", 20, "5"),
                log(T0, 20_000, DataSource.TESLA_LIVE, "2.0", "0.5", 30, "6"),
                log(T0.minusDays(1), 19_700, DataSource.TESLA_LIVE, "8", "2", 60, "11"));

        List<PublicCarResponse.Charge> out = PublicChargeGrouper.group(logs, 10);

        assertEquals(3, out.size());
        assertEquals(1, out.get(0).sessions());
        PublicCarResponse.Charge group = out.get(1);
        assertEquals(3, group.sessions());
        assertEquals(T0.plusDays(2).toLocalDate(), group.chargedOn(), "Datum vom letzten Vorgang der Gruppe");
        assertEquals(0, new BigDecimal("4.5").compareTo(group.kwhCharged()));
        assertEquals(65, group.durationMinutes());
        assertEquals(0, new BigDecimal("6").compareTo(group.maxChargingPowerKw()));
        assertEquals(1, out.get(2).sessions());
    }

    @Test
    void verbrauchDerGruppeKommtVomAeltestenVorgang() {
        // Nur der erste Vorgang am Ort hat den gefahrenen Abschnitt davor, die Nachladungen haben 0 km.
        List<EvLogResponse> logs = List.of(
                logWithConsumption(T0.plusDays(1), 20_000, null),
                logWithConsumption(T0, 20_000, "18.5"));
        List<PublicCarResponse.Charge> out = PublicChargeGrouper.group(logs, 10);
        assertEquals(1, out.size());
        assertEquals(0, new BigDecimal("18.5").compareTo(out.get(0).consumptionKwhPer100km()));
    }

    @Test
    void logOhneOdometerUnterbrichtDieOdometerKette() {
        List<EvLogResponse> logs = List.of(
                log(T0.plusDays(2), 20_000, DataSource.USER_LOGGED, "1", null, null, null),
                log(T0.plusDays(1), null, DataSource.USER_LOGGED, "1", null, null, null),
                log(T0, 20_000, DataSource.USER_LOGGED, "1", null, null, null));
        assertEquals(3, PublicChargeGrouper.group(logs, 10).size());
    }

    @Test
    void limitGiltNachDemGruppieren() {
        List<EvLogResponse> logs = List.of(
                log(T0.plusDays(2), 300, DataSource.USER_LOGGED, "1", null, null, null),
                log(T0.plusDays(1), 200, DataSource.USER_LOGGED, "1", null, null, null),
                log(T0.plusHours(1), 100, DataSource.USER_LOGGED, "1", null, null, null),
                log(T0, 100, DataSource.USER_LOGGED, "1", null, null, null));
        List<PublicCarResponse.Charge> out = PublicChargeGrouper.group(logs, 3);
        assertEquals(3, out.size());
        assertEquals(2, out.get(2).sessions());
    }

    @Test
    void summenBleibenNullWennKeinVorgangEinenWertHat() {
        List<EvLogResponse> logs = List.of(
                log(T0.plusHours(1), 100, DataSource.USER_LOGGED, null, null, null, null),
                log(T0, 100, DataSource.USER_LOGGED, null, null, null, null));
        PublicCarResponse.Charge c = PublicChargeGrouper.group(logs, 10).get(0);
        assertNull(c.kwhCharged());
        assertNull(c.costEur());
        assertNull(c.durationMinutes());
        assertNull(c.maxChargingPowerKw());
    }

    private static EvLogResponse logWithConsumption(OffsetDateTime at, Integer odo, String consumption) {
        return build(at, odo, DataSource.TESLA_LIVE, "1", null, null, null, consumption);
    }

    private static EvLogResponse log(OffsetDateTime at, Integer odo, DataSource source, String kwh, String cost, Integer minutes, String peakKw) {
        return build(at, odo, source, kwh, cost, minutes, peakKw, null);
    }

    private static EvLogResponse build(OffsetDateTime at, Integer odo, DataSource source, String kwh, String cost,
                                       Integer minutes, String peakKw, String consumption) {
        return new EvLogResponse(UUID.randomUUID(), UUID.randomUUID(),
                kwh == null ? null : new BigDecimal(kwh), null, cost == null ? null : new BigDecimal(cost),
                minutes, null, odo, peakKw == null ? null : new BigDecimal(peakKw), null, null,
                at, at, at, null, null, null,
                consumption == null ? null : new BigDecimal(consumption), null, null, null, null, null,
                ChargingType.AC, null, null, source, true, false, null, null, null, null, null, false, false);
    }
}
