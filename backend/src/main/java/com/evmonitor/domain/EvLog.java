package com.evmonitor.domain;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class EvLog {

    private final UUID id;
    private final UUID carId;
    private final BigDecimal kwhCharged;
    private final BigDecimal costEur;
    private final Integer chargeDurationMinutes;
    private final String geohash; // 6-char geohash (~600m) for private charging, 7-char (~150m) for public chargers
    private final Integer odometerKm; // Optional: odometer reading in km
    private final BigDecimal maxChargingPowerKw; // Optional: max charging power in kW
    private final Integer socAfterChargePercent; // Optional: State of Charge after charging (0-100%)
    private final Integer socBeforeChargePercent; // Optional: State of Charge before charging (0-100%)
    private final LocalDateTime loggedAt; // When the charge happened (user-provided or now)
    private final DataSource dataSource;
    private final boolean includeInStatistics; // Whether to include in public stats/aggregations
    private final Integer odometerSuggestionMinKm; // Optional: km estimate min from wallbox service
    private final Integer odometerSuggestionMaxKm; // Optional: km estimate max from wallbox service
    private final Double temperatureCelsius; // Optional: ambient temperature at charging time/location (Open-Meteo)
    private final ChargingType chargingType; // Optional: AC or DC charging
    private final String rawImportData;      // Optional: raw JSON data from import source (e.g. SpritMonitor fueling)
    private final RouteType routeType;       // Optional: CITY, COMBINED, or HIGHWAY
    private final TireType tireType;         // Optional: SUMMER, ALL_YEAR, or WINTER
    private final UUID supersededBy;         // Optional: ID of the USER_LOGGED log that supersedes this import
    private final UUID sessionGroupId;       // Optional: ID of the charging_session_group (sub-sessions only)
    private final boolean publicCharging;    // Whether this was at a public charger (CPO)
    private final String cpoName;            // Optional: CPO name (e.g. IONITY, EnBW) - only when isPublicCharging
    private final EnergyMeasurementType measurementType; // At which point energy is measured (derived from dataSource)
    private final BigDecimal costExchangeRate; // EUR->local rate used at entry time (null = EUR direct)
    private final String costCurrency;         // ISO 4217 currency code (null = EUR)
    private final UUID chargingProviderId;   // Optional: FK to user_charging_providers (which tariff was used)
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    // Full constructor - only called by the Lombok-generated builder.
    // Applies normalisation of loggedAt, dataSource defaults, and charging-type inference.
    @Builder(toBuilder = true)
    private EvLog(UUID id, UUID carId, BigDecimal kwhCharged, BigDecimal costEur,
            Integer chargeDurationMinutes, String geohash, Integer odometerKm,
            BigDecimal maxChargingPowerKw, Integer socAfterChargePercent, Integer socBeforeChargePercent,
            LocalDateTime loggedAt, DataSource dataSource,
            boolean includeInStatistics, Integer odometerSuggestionMinKm, Integer odometerSuggestionMaxKm,
            Double temperatureCelsius, ChargingType chargingType, String rawImportData,
            LocalDateTime createdAt, LocalDateTime updatedAt,
            RouteType routeType, TireType tireType, UUID supersededBy, UUID sessionGroupId,
            boolean publicCharging, String cpoName, EnergyMeasurementType measurementType,
            BigDecimal costExchangeRate, String costCurrency, UUID chargingProviderId) {
        this.id = id;
        this.carId = carId;
        this.kwhCharged = kwhCharged;
        this.costEur = costEur;
        this.chargeDurationMinutes = chargeDurationMinutes;
        this.geohash = geohash;
        this.odometerKm = odometerKm;
        this.maxChargingPowerKw = maxChargingPowerKw;
        this.socAfterChargePercent = socAfterChargePercent;
        this.socBeforeChargePercent = socBeforeChargePercent;
        LocalDateTime base = loggedAt != null ? loggedAt : LocalDateTime.now();
        this.loggedAt = base.withSecond(0).withNano(0);
        this.dataSource = dataSource != null ? dataSource : DataSource.USER_LOGGED;
        this.measurementType = measurementType != null ? measurementType : this.dataSource.measurementType();
        this.includeInStatistics = includeInStatistics;
        this.odometerSuggestionMinKm = odometerSuggestionMinKm;
        this.odometerSuggestionMaxKm = odometerSuggestionMaxKm;
        this.temperatureCelsius = temperatureCelsius;
        this.chargingType = inferChargingType(chargingType, maxChargingPowerKw, kwhCharged, chargeDurationMinutes);
        this.rawImportData = rawImportData;
        this.routeType = routeType;
        this.tireType = tireType;
        this.supersededBy = supersededBy;
        this.sessionGroupId = sessionGroupId;
        this.publicCharging = publicCharging;
        this.cpoName = cpoName;
        this.costExchangeRate = costExchangeRate;
        this.costCurrency = costCurrency;
        this.chargingProviderId = chargingProviderId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static EvLog createNew(UUID carId, BigDecimal kwhCharged, BigDecimal costEur,
            Integer chargeDurationMinutes, String geohash, Integer odometerKm,
            BigDecimal maxChargingPowerKw, Integer socAfterChargePercent, LocalDateTime loggedAt,
            ChargingType chargingType, RouteType routeType, TireType tireType,
            boolean publicCharging, String cpoName) {
        LocalDateTime now = LocalDateTime.now();
        return EvLog.builder()
                .id(UUID.randomUUID())
                .carId(carId)
                .kwhCharged(kwhCharged)
                .costEur(costEur)
                .chargeDurationMinutes(chargeDurationMinutes)
                .geohash(geohash)
                .odometerKm(odometerKm)
                .maxChargingPowerKw(maxChargingPowerKw)
                .socAfterChargePercent(socAfterChargePercent)
                .loggedAt(loggedAt)
                .dataSource(DataSource.USER_LOGGED)
                .includeInStatistics(true)
                .chargingType(chargingType)
                .routeType(routeType)
                .tireType(tireType)
                .publicCharging(publicCharging)
                .cpoName(cpoName)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public static EvLog createNewWithSource(UUID carId, BigDecimal kwhCharged, BigDecimal costEur,
            Integer chargeDurationMinutes, String geohash, Integer odometerKm,
            BigDecimal maxChargingPowerKw, Integer socAfterChargePercent, LocalDateTime loggedAt,
            DataSource dataSource, ChargingType chargingType, String rawImportData) {
        LocalDateTime now = LocalDateTime.now();
        return EvLog.builder()
                .id(UUID.randomUUID())
                .carId(carId)
                .kwhCharged(kwhCharged)
                .costEur(costEur)
                .chargeDurationMinutes(chargeDurationMinutes)
                .geohash(geohash)
                .odometerKm(odometerKm)
                .maxChargingPowerKw(maxChargingPowerKw)
                .socAfterChargePercent(socAfterChargePercent)
                .loggedAt(loggedAt)
                .dataSource(dataSource)
                .includeInStatistics(dataSource.includeInStatistics())
                .chargingType(chargingType)
                .rawImportData(rawImportData)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public static EvLog createFromInternal(UUID carId, BigDecimal kwhCharged,
            Integer chargeDurationMinutes, String geohash,
            LocalDateTime loggedAt, Integer odometerSuggestionMinKm, Integer odometerSuggestionMaxKm,
            DataSource dataSource, BigDecimal costEur, ChargingType chargingType,
            Integer odometerKm, Integer socBefore, Integer socAfter, Double temperatureCelsius) {
        LocalDateTime now = LocalDateTime.now();
        return EvLog.builder()
                .id(UUID.randomUUID())
                .carId(carId)
                .kwhCharged(kwhCharged)
                .costEur(costEur)
                .chargeDurationMinutes(chargeDurationMinutes)
                .geohash(geohash)
                .odometerKm(odometerKm)
                .socAfterChargePercent(socAfter)
                .socBeforeChargePercent(socBefore)
                .loggedAt(loggedAt)
                .dataSource(dataSource)
                .includeInStatistics(dataSource.includeInStatistics())
                .odometerSuggestionMinKm(odometerSuggestionMinKm)
                .odometerSuggestionMaxKm(odometerSuggestionMaxKm)
                .temperatureCelsius(temperatureCelsius)
                .chargingType(chargingType)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public static EvLog createFromPublicApi(UUID carId, BigDecimal kwhCharged, BigDecimal costEur,
            Integer chargeDurationMinutes, String geohash, Integer odometerKm,
            BigDecimal maxChargingPowerKw, Integer socAfterChargePercent, Integer socBeforeChargePercent,
            LocalDateTime loggedAt, ChargingType chargingType, RouteType routeType, TireType tireType,
            DataSource dataSource, String rawImportData, boolean publicCharging, String cpoName,
            EnergyMeasurementType measurementType) {
        LocalDateTime now = LocalDateTime.now();
        return EvLog.builder()
                .id(UUID.randomUUID())
                .carId(carId)
                .kwhCharged(kwhCharged)
                .costEur(costEur)
                .chargeDurationMinutes(chargeDurationMinutes)
                .geohash(geohash)
                .odometerKm(odometerKm)
                .maxChargingPowerKw(maxChargingPowerKw)
                .socAfterChargePercent(socAfterChargePercent)
                .socBeforeChargePercent(socBeforeChargePercent)
                .loggedAt(loggedAt)
                .dataSource(dataSource)
                .includeInStatistics(dataSource.includeInStatistics())
                .chargingType(chargingType)
                .rawImportData(rawImportData)
                .routeType(routeType)
                .tireType(tireType)
                .publicCharging(publicCharging)
                .cpoName(cpoName)
                .measurementType(measurementType)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private static ChargingType inferChargingType(ChargingType given, BigDecimal maxPowerKw,
                                                   BigDecimal kwhCharged, Integer durationMinutes) {
        if (given != null && given != ChargingType.UNKNOWN) return given;
        if (maxPowerKw != null) {
            return maxPowerKw.compareTo(BigDecimal.valueOf(22)) > 0 ? ChargingType.DC : ChargingType.AC;
        }
        if (kwhCharged != null && durationMinutes != null && durationMinutes >= 1) {
            double avgKw = kwhCharged.doubleValue() / (durationMinutes / 60.0);
            return avgKw > 22 ? ChargingType.DC : ChargingType.AC;
        }
        return ChargingType.UNKNOWN;
    }

    public EvLog withPatch(BigDecimal kwh, BigDecimal costEur, Integer durationMin,
            String geohash, Integer odometerKm, Integer socBefore, Integer socAfter,
            BigDecimal maxChargingPowerKw, ChargingType chargingType,
            RouteType routeType, TireType tireType, Boolean publicCharging, String cpoName,
            EnergyMeasurementType measurementType,
            BigDecimal costExchangeRate, String costCurrency, UUID chargingProviderId) {
        return toBuilder()
                .kwhCharged(kwh != null ? kwh : this.kwhCharged)
                .costEur(costEur != null ? costEur : this.costEur)
                .costExchangeRate(costExchangeRate != null ? costExchangeRate : this.costExchangeRate)
                .costCurrency(costCurrency != null ? costCurrency : this.costCurrency)
                .chargeDurationMinutes(durationMin != null ? durationMin : this.chargeDurationMinutes)
                .geohash(geohash != null ? geohash : this.geohash)
                .odometerKm(odometerKm != null ? odometerKm : this.odometerKm)
                .maxChargingPowerKw(maxChargingPowerKw != null ? maxChargingPowerKw : this.maxChargingPowerKw)
                .socAfterChargePercent(socAfter != null ? socAfter : this.socAfterChargePercent)
                .socBeforeChargePercent(socBefore != null ? socBefore : this.socBeforeChargePercent)
                .chargingType(chargingType != null ? chargingType : this.chargingType)
                .routeType(routeType != null ? routeType : this.routeType)
                .tireType(tireType != null ? tireType : this.tireType)
                .publicCharging(publicCharging != null ? publicCharging : this.publicCharging)
                .cpoName(cpoName != null ? cpoName : this.cpoName)
                .measurementType(measurementType != null ? measurementType : this.measurementType)
                .chargingProviderId(chargingProviderId != null ? chargingProviderId : this.chargingProviderId)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public EvLog withIncludeInStatistics(boolean includeInStatistics) {
        return toBuilder()
                .includeInStatistics(includeInStatistics)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * A log is complete when all fields required to act as logY in a consumption
     * calculation are present: odometer, kwhCharged, and socAfterChargePercent.
     */
    public boolean isComplete() {
        return odometerKm != null && kwhCharged != null && socAfterChargePercent != null;
    }

    /**
     * A log can act as logX (the trip starting point) when odometer and
     * socAfterChargePercent are present. kwhCharged is not required for this role.
     */
    public boolean canBeUsedAsLogX() {
        return odometerKm != null && socAfterChargePercent != null;
    }

    /**
     * True if kwhCharged is present and positive - the minimum required for any energy accounting.
     */
    public boolean hasKwhCharged() {
        return kwhCharged != null && kwhCharged.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * True if this log falls within the given date range (both bounds inclusive, both nullable).
     * A null startDate means no lower bound; a null endDate means no upper bound.
     */
    public boolean isLoggedWithin(LocalDate startDate, LocalDate endDate) {
        LocalDate logDate = loggedAt.toLocalDate();
        boolean afterStart = startDate == null || !logDate.isBefore(startDate);
        boolean beforeEnd = endDate == null || !logDate.isAfter(endDate);
        return afterStart && beforeEnd;
    }
}
