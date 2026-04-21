package com.evmonitor.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Testet das Verhalten von getEffectiveBatteryCapacityKwhAt() wenn ein Car
 * mit einer verifizierten Spec verknüpft ist (specNetBatteryCapacityKwh gesetzt).
 *
 * Kernregel: specNetBatteryCapacityKwh hat Vorrang vor batteryCapacityKwh
 * als Basis für alle Kapazitäts-/SoH-Berechnungen.
 */
class CarSpecNetCapacityTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 4, 21);

    private Car carWithNominalAndSpec(BigDecimal nominal, BigDecimal specNet) {
        return Car.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID())
                .model(CarBrand.CarModel.ENYAQ).year(2025)
                .licensePlate("TEST").trim("85 Sportline")
                .batteryCapacityKwh(nominal).powerKw(new BigDecimal("210"))
                .registrationDate(LocalDate.of(2025, 1, 1))
                .status(CarStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .specNetBatteryCapacityKwh(specNet)
                .build();
    }

    private BatterySohEntry sohEntry(BigDecimal sohPercent, LocalDate date) {
        return new BatterySohEntry(UUID.randomUUID(), UUID.randomUUID(), sohPercent, date, LocalDateTime.now());
    }

    // -------------------------------------------------------------------------
    // specNetBatteryCapacityKwh gesetzt → wird als Basis verwendet
    // -------------------------------------------------------------------------

    @Test
    void usesSpecNet_asBase_whenLinked() {
        // User hat 82 kWh eingetragen (Brutto Enyaq 85), Spec sagt 77 kWh netto
        Car car = carWithNominalAndSpec(new BigDecimal("82.0"), new BigDecimal("77.0"));

        BigDecimal result = car.getEffectiveBatteryCapacityKwhAt(TODAY, List.of());

        assertEquals(0, new BigDecimal("77").compareTo(result), "Erwartet 77 kWh netto als Basis");
    }

    @Test
    void appliesSohToSpecNet_notToNominal() {
        // 77 kWh netto × 90% SoH = 69.30 kWh (nicht 82 × 90% = 73.80)
        Car car = carWithNominalAndSpec(new BigDecimal("82.0"), new BigDecimal("77.0"));
        BatterySohEntry entry = sohEntry(new BigDecimal("90.0"), TODAY.minusDays(10));

        BigDecimal result = car.getEffectiveBatteryCapacityKwhAt(TODAY, List.of(entry));

        assertEquals(new BigDecimal("69.30"), result);
    }

    @Test
    void usesSpecNet_whenSohHistoryIsBeforeFirstEntry() {
        // Vor dem ersten SoH-Eintrag: Basis ist specNet (unverdegradiert)
        Car car = carWithNominalAndSpec(new BigDecimal("82.0"), new BigDecimal("77.0"));
        BatterySohEntry futureEntry = sohEntry(new BigDecimal("95.0"), TODAY.plusDays(1));

        BigDecimal result = car.getEffectiveBatteryCapacityKwhAt(TODAY, List.of(futureEntry));

        assertEquals(0, new BigDecimal("77").compareTo(result), "Erwartet 77 kWh netto vor erstem SoH-Eintrag");
    }

    // -------------------------------------------------------------------------
    // specNetBatteryCapacityKwh null → Fallback auf batteryCapacityKwh
    // -------------------------------------------------------------------------

    @Test
    void fallsBackToUserEntry_whenNoSpecLinked() {
        Car car = carWithNominalAndSpec(new BigDecimal("82.0"), null);

        BigDecimal result = car.getEffectiveBatteryCapacityKwhAt(TODAY, List.of());

        assertEquals(new BigDecimal("82.0"), result);
    }

    @Test
    void appliesSohToUserEntry_whenNoSpecLinked() {
        Car car = carWithNominalAndSpec(new BigDecimal("82.0"), null);
        BatterySohEntry entry = sohEntry(new BigDecimal("90.0"), TODAY.minusDays(1));

        BigDecimal result = car.getEffectiveBatteryCapacityKwhAt(TODAY, List.of(entry));

        assertEquals(new BigDecimal("73.80"), result);
    }

    @Test
    void returnsNull_whenBothCapacitiesAreNull() {
        Car car = carWithNominalAndSpec(null, null);

        assertNull(car.getEffectiveBatteryCapacityKwhAt(TODAY, List.of()));
    }
}
