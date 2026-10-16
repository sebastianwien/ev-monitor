package com.evmonitor.application.vehiclespec;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Antworten des Admin-Tabs Fahrzeug-Specs. */
public final class AdminSpecResponses {

    private AdminSpecResponses() {
    }

    /** Arbeitslisten, serverseitig je Zeile berechnet, damit die Regeln an einer Stelle liegen. */
    public enum WorkList { NO_NAME, NO_TRIM, NO_PERIOD, NO_SOURCE, NO_NET, USER_ROW, ROW_WITHOUT_ENUM_MODEL }

    public record SpecRow(UUID id, String carBrand, String carModel, BigDecimal batteryCapacityKwh,
                          BigDecimal netBatteryCapacityKwh, BigDecimal officialRangeKm,
                          BigDecimal officialConsumptionKwhPer100km, String ratingSource, String variantName,
                          String trimLevel, LocalDate availableFrom, LocalDate availableTo, String sourceUrl,
                          BigDecimal dcMaxPowerKw, BigDecimal dcAvgPower1080Kw, long activeCars, long allCars, long ratings, List<WorkList> workLists) {
    }

    public record ModelGroup(String carBrand, String carModel, String displayName, boolean inEnum,
                             long logCount, long activeCars, long carsWithoutSpec, List<SpecRow> rows) {
    }

    /** Enum-Modell ohne Zeile; solange die Liste Autos enthält, bleibt der Enum-Fallback im CarController. */
    public record ModelWithoutRow(String carBrand, String carModel, String displayName, long activeCars, long logCount) {
    }

    public record Overview(List<ModelGroup> models, List<ModelWithoutRow> modelsWithoutRow, long carsWithoutSpec) {
    }

    public record SaveResult(SpecRow row, List<String> warnings) {
    }

    public record Candidate(UUID specId, String variantName, String trimLevel, BigDecimal netKwh, BigDecimal diffKwh,
                            LocalDate availableFrom, LocalDate availableTo) {
    }

    public record CarForReassign(UUID carId, String carModel, Integer year, String trim, BigDecimal currentNetKwh,
                                 UUID currentSpecId, long logCount, List<Candidate> candidates) {
    }

    public record ChangeEntry(UUID id, UUID adminUserId, LocalDateTime changedAt, String action, String field,
                              String oldValue, String newValue, String warning) {
    }
}
