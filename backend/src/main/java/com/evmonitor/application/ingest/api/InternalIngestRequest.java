package com.evmonitor.application.ingest.api;

import com.evmonitor.domain.DataSource;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Vertrag B: was ein interner Dienst (Connectors) dem Core übergibt - Ladungen und Fahrten eines
 * Autos aus einer Quelle. Felder stehen in {@code contract/internal-api.json}.
 *
 * <p>Die Quelle ist streng (unbekannt = 400), weil sie die Regeln der Annahme bestimmt. Ladeart und
 * Energieherkunft bleiben nachsichtig wie bei {@code /api/internal/logs}: lieber ohne Angabe
 * speichern als die Ladung verlieren.
 *
 * @param externalRef Verweis auf die Rohdaten beim Absender (z. B. Drop-Id), nur fürs Log
 */
public record InternalIngestRequest(
        @NotNull DataSource dataSource,
        @Size(max = 200) String externalRef,
        @NotNull UUID userId,
        @NotNull UUID carId,
        @Size(max = InternalIngestRequest.MAX_ENTRIES) List<@NotNull @Valid ChargingSession> chargingSessions,
        @Size(max = InternalIngestRequest.MAX_ENTRIES) List<@NotNull @Valid Trip> trips) {

    public static final int MAX_ENTRIES = 500;

    @JsonIgnore
    @AssertTrue(message = "chargingSessions or trips must not be empty")
    public boolean isAnyEntry() {
        return (chargingSessions != null && !chargingSessions.isEmpty()) || (trips != null && !trips.isEmpty());
    }

    public List<ChargingSession> chargingSessionsOrEmpty() {
        return chargingSessions != null ? chargingSessions : List.of();
    }

    public List<Trip> tripsOrEmpty() {
        return trips != null ? trips : List.of();
    }

    /**
     * @param loggedAt ISO ohne Offset, Semantik wie {@code /api/internal/logs}
     */
    public record ChargingSession(
            @NotNull LocalDateTime loggedAt,
            BigDecimal kwhCharged,
            Integer chargeDurationMinutes,
            String geohash,
            Integer odometerKm,
            Integer odometerSuggestionMinKm,
            Integer odometerSuggestionMaxKm,
            BigDecimal costEur,
            BigDecimal pricePerKwh,
            String chargingType,
            String energySource,
            BigDecimal socBefore,
            BigDecimal socAfter,
            Boolean socStartMissed,
            Double temperatureCelsius,
            String rawImportData,
            Boolean publicCharging,
            String cpoName,
            BigDecimal maxChargingPowerKw,
            Extras extras) {
    }

    /** Ladekurven als JSON-Arrays, siehe {@code InternalEvLogRequest}. */
    public record Extras(String powerCurvePointsJson, String socCurvePointsJson) {
    }

    /** @param externalId Pflicht: macht die Wiederholung eines Aufrufs sicher */
    public record Trip(
            @NotNull UUID externalId,
            @NotNull OffsetDateTime tripStartedAt,
            OffsetDateTime tripEndedAt,
            BigDecimal socStart,
            BigDecimal socEnd,
            BigDecimal odometerStartKm,
            BigDecimal odometerEndKm,
            BigDecimal distanceKm,
            String locationStartGeohash,
            String locationEndGeohash,
            BigDecimal outsideTempCelsius,
            BigDecimal energyRemainingStartKwh,
            BigDecimal energyRemainingEndKwh,
            BigDecimal estimatedConsumedKwh,
            BigDecimal avgSpeedKmh,
            BigDecimal maxSpeedKmh,
            String status,
            String rawPayload,
            String telemetryExtras,
            String tracePolyline) {
    }
}
