package com.evmonitor.application;

import ch.hsr.geohash.GeoHash;
import com.evmonitor.application.consumption.ConsumptionCalculationService;
import com.evmonitor.domain.*;
import com.evmonitor.domain.exception.ConflictException;
import com.evmonitor.domain.exception.ForbiddenException;
import com.evmonitor.domain.exception.NotFoundException;

import com.evmonitor.infrastructure.persistence.JpaUserChargingProviderRepository;
import com.evmonitor.infrastructure.persistence.UserChargingProviderEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * CRUD service for charging logs. Ownership checks, creation, update, delete.
 * Statistics and community aggregates live in EvLogStatisticsService.
 * Pure calculation logic lives in ConsumptionCalculationService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EvLogService {

    /**
     * Maximaler zeitlicher Abstand zweier Logs, damit sie zusammengefuehrt werden duerfen.
     * 24h, damit auch sehr langsame AC-Ladevorgaenge (z.B. 14h an 4 kW) noch abgedeckt sind.
     */
    public static final Duration MERGE_WINDOW = Duration.ofHours(24);

    private final EvLogRepository evLogRepository;
    private final CarRepository carRepository;
    private final VehicleSpecificationRepository vehicleSpecificationRepository;
    private final CoinLogService coinLogService;
    private final PlausibilityProperties plausibility;
    private final ConsumptionCalculationService calculationService;
    private final JpaUserChargingProviderRepository chargingProviderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final LocationPricing locationPricing;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Transactional
    public EvLogCreateResponse logCharging(UUID userId, EvLogRequest request) {
        Car car = carRepository.findById(request.carId())
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));

        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }

        // Convert lat/lon to geohash for privacy. Public chargers get 7-char (~150m), private get 6-char (~600m).
        // Lat/lon are never stored, only the anonymized geohash.
        String geohash = null;
        if (request.latitude() != null && request.longitude() != null) {
            int precision = Boolean.TRUE.equals(request.isPublicCharging()) ? 7 : 6;
            geohash = GeoHash.withCharacterPrecision(request.latitude(), request.longitude(), precision).toBase32();
        }

        EvLog newLog = EvLog.createNew(
                request.carId(),
                request.kwhCharged(),
                request.costEur(),
                request.chargeDurationMinutes(),
                geohash,
                request.odometerKm(),
                request.maxChargingPowerKw(),
                request.socAfterChargePercent(),
                request.loggedAt(),
                request.chargingType(),
                request.routeType(),
                request.tireType(),
                Boolean.TRUE.equals(request.isPublicCharging()),
                request.cpoName());

        var builder = newLog.toBuilder()
                .socBeforeChargePercent(request.socBeforeChargePercent())
                .kwhAtVehicle(request.kwhAtVehicle());
        if (request.kwhAtVehicle() != null && request.kwhCharged() == null) {
            builder.measurementType(EnergyMeasurementType.AT_VEHICLE);
        }
        if (request.costCurrency() != null && request.costExchangeRate() != null) {
            builder.costExchangeRate(request.costExchangeRate())
                   .costCurrency(request.costCurrency());
        }
        if (request.chargingProviderId() != null) {
            if (!chargingProviderRepository.existsByIdAndUserIdAndDeletedAtIsNull(request.chargingProviderId(), userId)) {
                throw new IllegalArgumentException("Charging provider not found for current user");
            }
            builder.chargingProviderId(request.chargingProviderId());
        }
        newLog = locationPricing.enrich(builder.build(), userId);

        EvLog savedLog = save(newLog);

        // Award coins for this log entry. CoinEvent determines first vs. subsequent, with optional OCR bonus.
        // First-time detection is via coin history (immutable), not log count — prevents delete-and-recreate farming.
        // NOTE: ocrUsed is client-supplied and not server-verifiable — the +2 bonus is accepted risk
        // (low value, requires conscious manipulation, not worth server-side OCR session tracking).
        CoinLogService.CoinEvent coinEvent;
        if (Boolean.TRUE.equals(request.ocrUsed())) {
            boolean firstOcrEver = !coinLogService.hasEverReceivedCoinForAction(
                    userId, CoinLogService.CoinEvent.MANUAL_LOG_FIRST_OCR.getDescription());
            coinEvent = firstOcrEver
                    ? CoinLogService.CoinEvent.MANUAL_LOG_FIRST_OCR
                    : CoinLogService.CoinEvent.MANUAL_LOG_OCR;
        } else {
            boolean firstLogEver = !coinLogService.hasEverReceivedCoinForAction(
                    userId, CoinLogService.CoinEvent.MANUAL_LOG_FIRST.getDescription());
            coinEvent = firstLogEver
                    ? CoinLogService.CoinEvent.MANUAL_LOG_FIRST
                    : CoinLogService.CoinEvent.MANUAL_LOG_SUBSEQUENT;
        }
        int coinsAwarded = coinLogService.awardCoinsForEvent(userId, coinEvent, savedLog.getId());

        return new EvLogCreateResponse(EvLogResponse.fromDomain(savedLog), coinsAwarded);
    }

    /**
     * Creates a charging log on behalf of a user from an OCPP wallbox session.
     * Called by the internal Wallbox Service — not user-facing.
     */
    @Transactional
    public EvLogResponse createInternalLog(InternalEvLogRequest request) {
        Car car = carRepository.findById(request.carId())
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));

        if (!car.getUserId().equals(request.userId())) {
            throw new IllegalArgumentException("Car does not belong to user");
        }

        DataSource source = DataSource.WALLBOX_OCPP;
        if (request.dataSource() != null) {
            try { source = DataSource.valueOf(request.dataSource()); } catch (IllegalArgumentException ignored) {}
        }

        // Idempotent: skip if already imported (same car + timestamp + data source).
        // loggedAt wird im EvLog-Konstruktor auf Minuten truncated, daher exakter Match sicher.
        LocalDateTime loggedAtTruncated = request.loggedAt() != null ? request.loggedAt().withSecond(0).withNano(0) : LocalDateTime.now().withSecond(0).withNano(0);
        if (evLogRepository.existsByCarIdAndLoggedAtAndDataSource(request.carId(), loggedAtTruncated, source)) {
            return null;
        }

        ChargingType chargingType = ChargingType.UNKNOWN;
        if (request.chargingType() != null) {
            try { chargingType = ChargingType.valueOf(request.chargingType()); } catch (IllegalArgumentException ignored) {}
        }

        // Provenance of kwhCharged - decides whether SoH auto-detection trusts this row.
        // Unknown strings fall back to null (= legacy behaviour, trusted) instead of failing
        // the whole request, mirroring the dataSource handling above. The CHECK constraint on
        // ev_log.energy_source would otherwise reject anything outside the enum.
        EnergySource energySource = null;
        if (request.energySource() != null) {
            try { energySource = EnergySource.valueOf(request.energySource()); }
            catch (IllegalArgumentException ignored) {
                log.warn("Unknown energySource '{}' from internal client - storing as NULL", request.energySource());
            }
        }

        // R15: hat der Connector einen verpassten Ladestart gemeldet, ist socBefore null.
        // Aus den vollstaendigen Ladungen desselben Autos herleiten (median kWh/SoC-Punkt).
        BigDecimal socBefore = request.socBefore();
        if (Boolean.TRUE.equals(request.socStartMissed()) && socBefore == null) {
            socBefore = deriveMissedStartSoc(car, request);
        }

        EvLog newLog = EvLog.createFromInternal(
                request.carId(),
                request.kwhCharged(),
                request.chargeDurationMinutes(),
                request.geohash(),
                request.loggedAt(),
                request.odometerSuggestionMinKm(),
                request.odometerSuggestionMaxKm(),
                source,
                request.costEur(),
                chargingType,
                request.odometerKm(),
                socBefore,
                request.socAfter(),
                request.temperatureCelsius(),
                request.rawImportData(),
                request.isPublicCharging(),
                request.cpoName(),
                request.maxChargingPowerKw(),
                energySource,
                request.pricePerKwh());

        // Inherit tireType/routeType from the most recent prior log so auto-created logs
        // (Tesla/Wallbox/SmartCar) don't reset the user's last known setting to NULL/SUMMER.
        // Uses "before this log's timestamp" so late-arriving logs pick the contemporaneous value.
        newLog = inheritTireAndRouteType(newLog);

        newLog = locationPricing.enrich(newLog, request.userId());

        EvLog savedLog = save(newLog);

        // Power-curve-Snapshot (~30 Punkte) als JSONB neben den Log persistieren -
        // ueberlebt das 28-Tage-Retention-Limit auf vehicle_signal_events. Nur Tesla
        // FULL-Profil-Connectoren liefern hier was, alle anderen lassen NULL.
        if (request.socCurvePointsJson() != null && !request.socCurvePointsJson().isBlank()) {
            evLogRepository.updateSocCurvePoints(savedLog.getId(), request.socCurvePointsJson());
        }
        if (request.powerCurvePointsJson() != null && !request.powerCurvePointsJson().isBlank()) {
            evLogRepository.updatePowerCurvePoints(savedLog.getId(), request.powerCurvePointsJson());
        }

        // Award per-log coins for Tesla imports.
        // go-eCharger and plain OCPP wallbox coins are TBD and intentionally not awarded here yet.
        if (source == DataSource.TESLA_FLEET_IMPORT || source == DataSource.TESLA_LIVE) {
            coinLogService.awardCoinsForEvent(request.userId(), CoinLogService.CoinEvent.TESLA_DAILY_LOG, savedLog.getId());
        }

        // Internal logs trigger SoH even when only kwhCharged is present (no kwhAtVehicle).
        // save() already fires SohAutoDetectEvent when kwhAtVehicle != null.
        if (savedLog.getKwhAtVehicle() == null) {
            eventPublisher.publishEvent(new SohAutoDetectEvent(car));
        }

        return EvLogResponse.fromDomain(savedLog);
    }

    /**
     * Prüft ob ein User das angegebene Fahrzeug besitzt.
     * Wirft IllegalArgumentException bei Ownership-Verletzung (404-equivalent).
     */
    public void verifyCarOwnership(UUID carId, UUID userId) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));
        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }
    }

    /**
     * Minimale Projektion eines Tesla-Logs, das noch keine Kosten traegt.
     *
     * <p>{@code chargeDurationMinutes} ist nullable und erlaubt dem Connector, seine Nachlaeufe am
     * Lade-ENDE zu takten statt am Start - dazwischen liegt die gesamte Ladedauer.
     * {@code kwhCharged} dient dem Abgleich gegen die Energiemenge der Tesla-Session, damit bei
     * mehreren Fahrzeugen eines Users im selben Zeitfenster nicht das falsche Log bepreist wird.
     */
    public record EnrichableChargingLog(UUID id, LocalDateTime loggedAt, Integer chargeDurationMinutes,
                                        BigDecimal kwhCharged) {}

    /**
     * Listet Tesla-Ladungen des Users ohne Kosten, die aus Teslas Billing-API stammen koennten.
     * Der {@code TeslaSuperchargerEnrichmentJob} gleicht sie gegen {@code /dx/charging/history} ab -
     * welche davon Supercharger waren, weiss nur Tesla.
     */
    public List<EnrichableChargingLog> findEnrichableTeslaLogs(UUID userId, int days) {
        int safeDays = Math.max(1, days);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(safeDays);
        return evLogRepository.findEnrichableTeslaLogs(userId, cutoff)
                .stream()
                .map(e -> new EnrichableChargingLog(
                        e.getId(), e.getLoggedAt(), e.getChargeDurationMinutes(), e.getKwhCharged()))
                .toList();
    }

    /**
     * Traegt Tesla-Abrechnungsdaten in ein Tesla-Log nach. Das Repository erzwingt als
     * Defense-in-Depth costEur IS NULL, eine Tesla-Datenquelle und die Zugehoerigkeit zum
     * angegebenen User; der Aufruf ist damit idempotent und kann bei falscher id weder fremde
     * Quellen noch fremde Konten ueberschreiben.
     * @return true wenn geschrieben wurde, false wenn das Log bereits Kosten trug, die id auf kein
     *         anreicherbares Tesla-Log zeigt oder es einem anderen User gehoert.
     */
    @Transactional
    public boolean enrichWithTeslaPricing(UUID logId, UUID userId, BigDecimal costEur, String cpoName) {
        BigDecimal cost = costEur != null ? costEur : BigDecimal.ZERO;
        int affected = evLogRepository.enrichWithTeslaPricing(logId, userId, cost, cpoName);
        if (affected == 0) {
            log.debug("Tesla SuC enrichment skipped for log {} (already enriched or non-pending)", logId);
        }
        return affected > 0;
    }

    @Transactional
    public void updateGeohash(UUID carId, UUID userId, LocalDateTime loggedAt, String geohash) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));
        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Car does not belong to user");
        }
        evLogRepository.updateGeohash(carId, loggedAt, geohash).ifPresent(evLog -> {
            EvLog enriched = locationPricing.enrich(evLog, userId);
            if (enriched != evLog) evLogRepository.save(enriched);
        });
    }

    /**
     * Backfills measured ambient temperature on an existing log (matched by carId + loggedAt),
     * only when it has none yet. Used by the connector to fill TESLA_LIVE logs from streamed
     * OutsideTemp. Returns {@code true} when a log was updated.
     */
    @Transactional
    public boolean backfillTemperature(UUID carId, UUID userId, LocalDateTime loggedAt, Double temperatureCelsius) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));
        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Car does not belong to user");
        }
        return evLogRepository.updateTemperatureIfAbsent(carId, loggedAt, temperatureCelsius);
    }

    /**
     * Fills in tireType/routeType from the most recent prior log (per car, by loggedAt) when
     * the incoming log left them null. Auto-creation paths (Tesla telemetry, OCPP wallbox,
     * SmartCar) don't carry this metadata, so without inheritance every auto-log writes NULL
     * and the frontend's "carry over last value" UX silently drifts to SUMMER/COMBINED.
     * Uses "before loggedAt" so late-arriving telemetry inherits the value that was current
     * at the time of the charge, not whatever happens to be the latest in DB.
     */
    private EvLog inheritTireAndRouteType(EvLog log) {
        if (log.getTireType() != null && log.getRouteType() != null) return log;
        LocalDateTime before = log.getLoggedAt() != null ? log.getLoggedAt() : LocalDateTime.now();
        EvLog.EvLogBuilder builder = log.toBuilder();
        boolean changed = false;
        if (log.getTireType() == null) {
            Optional<TireType> inherited = evLogRepository.findMostRecentTireTypeBefore(log.getCarId(), before);
            if (inherited.isPresent()) {
                builder.tireType(inherited.get());
                changed = true;
            }
        }
        if (log.getRouteType() == null) {
            Optional<RouteType> inherited = evLogRepository.findMostRecentRouteTypeBefore(log.getCarId(), before);
            if (inherited.isPresent()) {
                builder.routeType(inherited.get());
                changed = true;
            }
        }
        return changed ? builder.build() : log;
    }

    /**
     * How many logs of this user at this location still have no cost. Drives the
     * "apply to all N charges here" prompt in the log form.
     */
    public long countPricelessLogsAtLocation(UUID userId, String geohash) {
        return evLogRepository.findPricelessLogsAtGeohash(userId, geohash).size();
    }

    /**
     * Retroactively prices every cost-less log of this user at this location with the given
     * charging card. Existing costs are never touched - the user's own numbers always win.
     * Returns how many logs were priced.
     *
     * This is the path an import-only user needs: {@link #applyPriceSuggestion} anchors on a
     * previous log that already carries a providerId, and an import produces none.
     */
    @Transactional
    public int applyTariffAtLocation(UUID userId, String geohash, UUID providerId) {
        if (!chargingProviderRepository.existsByIdAndUserIdAndDeletedAtIsNull(providerId, userId)) {
            throw new IllegalArgumentException("Charging provider does not belong to user");
        }
        UserChargingProviderEntity provider = chargingProviderRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Charging provider not found"));

        int priced = 0;
        for (EvLog log : evLogRepository.findPricelessLogsAtGeohash(userId, geohash)) {
            Optional<BigDecimal> cost = locationPricing.costUnder(provider, log);
            if (cost.isEmpty()) continue;
            BigDecimal price = locationPricing.priceUnder(provider, log.getChargingType()).orElse(null);
            save(log.toBuilder().chargingProviderId(providerId).costEur(cost.get())
                    .pricePerKwh(price).build());
            priced++;
        }
        return priced;
    }

    @Transactional
    public EvLog save(EvLog evLog) {
        EvLog saved = evLogRepository.save(evLog);
        eventPublisher.publishEvent(new EvLogSavedEvent(saved.getId(), saved.getGeohash(), saved.getLoggedAt(), saved.getTemperatureCelsius()));
        if (saved.getKwhAtVehicle() != null) {
            carRepository.findById(saved.getCarId()).ifPresent(car ->
                    eventPublisher.publishEvent(new SohAutoDetectEvent(car)));
        }
        return saved;
    }

    public List<EvLogResponse> getStandaloneLogsForUser(UUID userId) {
        return evLogRepository.findAllByUserId(userId).stream()
                .map(EvLogResponse::fromDomain)
                .toList();
    }

    public EvLogResponse getLogByIdForUser(UUID id, UUID userId) {
        EvLog log = evLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Log not found with ID: " + id));

        Car car = carRepository.findById(log.getCarId())
                .orElseThrow(() -> new IllegalArgumentException("Associated car not found"));

        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Log not found for current user (ownership mismatch).");
        }

        return EvLogResponse.fromDomain(log);
    }

    /**
     * Returns the persisted downsampled charging-power curve for the given log,
     * gated by ownership AND the paid analytics entitlement. The raw Tesla data is
     * free, but the historical power-curve view stays premium for every brand, so a
     * non-entitled owner gets {@link PowerCurveResponse#empty()} - same as a log that
     * simply has no curve (most data sources). Throws {@link IllegalArgumentException}
     * on log-not-found or ownership mismatch - mapped to 404 by the controller.
     */
    @Transactional(readOnly = true)
    public PowerCurveResponse getPowerCurveForUser(UUID logId, User user) {
        // Single JOIN-Query: holt owner-userId + curve-JSON in einem DB-Roundtrip.
        // Spart zwei separate findById-Calls (Log + Car) gegenueber der vorherigen
        // Implementierung, ohne die Ownership-Garantie zu schwaechen.
        EvLogRepository.PowerCurveLookup lookup = evLogRepository.findOwnerIdAndPowerCurveJson(logId)
                .orElseThrow(() -> new IllegalArgumentException("Log not found with ID: " + logId));

        if (!lookup.ownerUserId().equals(user.getId())) {
            throw new IllegalArgumentException("Log not found for current user (ownership mismatch).");
        }

        // Historical power curves are a paid AutoSync-Live analytics feature. Ownership is
        // checked first so a non-owner still gets a 404 (no existence leak); a non-entitled
        // owner just gets an empty curve.
        // Die Leistungskurve bleibt premium, der Ladeverlauf nicht: er ist das,
        // was Quellen ohne Leistungsmessung ueberhaupt hergeben.
        if (!user.canViewLiveAnalytics()) {
            return user.canViewSocCurve()
                    ? parseSocCurve(logId, lookup.socCurvePointsJson())
                    : PowerCurveResponse.empty();
        }

        // Leistungskurve wenn vorhanden, sonst der gemessene Ladeverlauf. Beide
        // koennen nie gleichzeitig entstehen: die eine setzt Live-Leistung voraus,
        // der andere ist genau fuer die Quellen da, die keine liefern.
        String json = lookup.powerCurvePointsJson();
        if (json == null || json.isBlank()) {
            return parseSocCurve(logId, lookup.socCurvePointsJson());
        }
        try {
            List<PowerCurveResponse.Point> points = objectMapper.readValue(
                    json, objectMapper.getTypeFactory().constructCollectionType(List.class, PowerCurveResponse.Point.class));
            return PowerCurveResponse.ofPower(points);
        } catch (Exception e) {
            log.warn("Failed to parse power-curve JSON for log {}: {}", logId, e.getMessage());
            return PowerCurveResponse.empty();
        }
    }

    private PowerCurveResponse parseSocCurve(UUID logId, String json) {
        if (json == null || json.isBlank()) {
            return PowerCurveResponse.empty();
        }
        try {
            List<PowerCurveResponse.SocPoint> points = objectMapper.readValue(
                    json, objectMapper.getTypeFactory().constructCollectionType(List.class, PowerCurveResponse.SocPoint.class));
            return new PowerCurveResponse(List.of(), points);
        } catch (Exception e) {
            log.warn("Failed to parse soc-curve JSON for log {}: {}", logId, e.getMessage());
            return PowerCurveResponse.empty();
        }
    }

    @Transactional
    /**
     * R15: leitet den verpassten Start-SoC aus den vollstaendigen Ladungen desselben Autos her
     * (median kWh/SoC-Punkt), Fallback Nominalkapazitaet. Rein rechnend - siehe
     * {@link MissedStartSocEstimator}.
     */
    private BigDecimal deriveMissedStartSoc(Car car, InternalEvLogRequest request) {
        List<MissedStartSocEstimator.Charge> cleanCharges = evLogRepository
                .findRecentAtVehicleLogsWithSoc(car.getId(), 40).stream()
                .map(l -> new MissedStartSocEstimator.Charge(
                        l.getSocBeforeChargePercent(), l.getSocAfterChargePercent(), l.getKwhAtVehicle()))
                .toList();
        return MissedStartSocEstimator.estimateSocStart(
                request.socAfter(), request.kwhCharged(), cleanCharges, car.getNominalNetCapacityKwh());
    }

    /**
     * Ohne @Transactional laeuft das interne save() als Self-Invocation am Spring-Proxy vorbei -
     * dann wird SohAutoDetectEvent ohne aktive Transaktion publiziert, und der
     * AFTER_COMMIT-Listener verwirft es stillschweigend. Ein nachtraeglich ergaenztes
     * kwhAtVehicle wuerde die SoH-Erkennung also nie ausloesen.
     */
    @Transactional
    public EvLogResponse updateLog(UUID id, UUID userId, EvLogUpdateRequest request) {
        EvLog existing = evLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Log not found with ID: " + id));

        Car car = carRepository.findById(existing.getCarId())
                .orElseThrow(() -> new IllegalArgumentException("Associated car not found"));

        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Log not found for current user (ownership mismatch).");
        }

        // Compute new geohash from lat/lon if provided; otherwise keep existing.
        // Precision depends on isPublicCharging (new value if provided, else existing).
        // Boolean, nicht boolean: seit V166 kann der Ladeort unbekannt sein, und ein
        // primitives Ziel wuerde beim Entpacken von NULL knallen. Betrifft genau die
        // Logs, die die Telemetrie ohne Ortsangabe anlegt.
        Boolean updatedIsPublicCharging = request.isPublicCharging() != null
                ? request.isPublicCharging()
                : existing.getPublicCharging();
        String geohash = existing.getGeohash();
        boolean geohashChanged = false;
        if (request.latitude() != null && request.longitude() != null) {
            // Unbekannter Ladeort bekommt die private Praezision - im Zweifel weniger
            // genau speichern, nicht mehr.
            int precision = Boolean.TRUE.equals(updatedIsPublicCharging) ? 7 : 6;
            geohash = GeoHash.withCharacterPrecision(request.latitude(), request.longitude(), precision).toBase32();
            geohashChanged = !geohash.equals(existing.getGeohash());
        } else if (!Boolean.TRUE.equals(updatedIsPublicCharging) && geohash != null && geohash.length() > 6) {
            // Privacy: if switched from public→private without new coordinates, truncate to 6-char precision.
            geohash = geohash.substring(0, 6);
            geohashChanged = true;
        }

        UUID updatedChargingProviderId = existing.getChargingProviderId();
        if (request.chargingProviderId() != null) {
            if (!chargingProviderRepository.existsByIdAndUserIdAndDeletedAtIsNull(request.chargingProviderId(), userId)) {
                throw new IllegalArgumentException("Charging provider not found for current user");
            }
            updatedChargingProviderId = request.chargingProviderId();
        }

        // Both fields are independent: sending both keeps both, sending one with the other null clears the other.
        BigDecimal updatedKwhCharged = request.kwhCharged() != null ? request.kwhCharged() : existing.getKwhCharged();
        BigDecimal updatedKwhAtVehicle = request.kwhAtVehicle() != null ? request.kwhAtVehicle() : existing.getKwhAtVehicle();
        if (request.kwhAtVehicle() != null && request.kwhCharged() == null) updatedKwhCharged = null;
        if (request.kwhCharged() != null && request.kwhAtVehicle() == null) updatedKwhAtVehicle = null;

        EnergyMeasurementType updatedMeasurementType = (updatedKwhAtVehicle != null && updatedKwhCharged == null)
                ? EnergyMeasurementType.AT_VEHICLE
                : EnergyMeasurementType.AT_CHARGER;

        EvLog updated = existing.toBuilder()
                .kwhCharged(updatedKwhCharged)
                .kwhAtVehicle(updatedKwhAtVehicle)
                .measurementType(updatedMeasurementType)
                .costEur(request.costEur()                   != null ? request.costEur()                : existing.getCostEur())
                .chargeDurationMinutes(request.chargeDurationMinutes() != null ? request.chargeDurationMinutes() : existing.getChargeDurationMinutes())
                .geohash(geohash)
                .odometerKm(request.odometerKm()             != null ? request.odometerKm()             : existing.getOdometerKm())
                .maxChargingPowerKw(request.maxChargingPowerKw() != null ? request.maxChargingPowerKw() : existing.getMaxChargingPowerKw())
                .socAfterChargePercent(request.socAfterChargePercent() != null ? request.socAfterChargePercent() : existing.getSocAfterChargePercent())
                .socBeforeChargePercent(request.socBeforeChargePercent() != null ? request.socBeforeChargePercent() : existing.getSocBeforeChargePercent())
                .loggedAt(request.loggedAt()                 != null ? request.loggedAt()               : existing.getLoggedAt())
                .chargingType(request.chargingType()         != null ? request.chargingType()            : existing.getChargingType())
                .routeType(request.routeType()               != null ? request.routeType()               : existing.getRouteType())
                .tireType(request.tireType()                 != null ? request.tireType()                : existing.getTireType())
                .publicCharging(updatedIsPublicCharging)
                .cpoName(request.cpoName()                   != null ? request.cpoName()                 : existing.getCpoName())
                .costExchangeRate(request.costExchangeRate() != null ? request.costExchangeRate()     : existing.getCostExchangeRate())
                .costCurrency(request.costCurrency()         != null ? request.costCurrency()          : existing.getCostCurrency())
                .chargingProviderId(updatedChargingProviderId)
                .temperatureCelsius(geohashChanged ? null : existing.getTemperatureCelsius())
                .updatedAt(LocalDateTime.now())
                .build();

        EvLog savedLog = save(updated);

        return EvLogResponse.fromDomain(savedLog);
    }

    @Transactional
    public void deleteLog(UUID id, UUID userId) {
        EvLog log = evLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Log not found with ID: " + id));

        Car car = carRepository.findById(log.getCarId())
                .orElseThrow(() -> new IllegalArgumentException("Associated car not found"));

        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Log not found for current user (ownership mismatch).");
        }

        // Deduct coins that were awarded for this log (identified via source_entity_id).
        // Only deduct if coins were actually awarded — prevents creating negative phantom entries.
        int coinSum = coinLogService.sumCoinsForSourceEntity(id);
        if (coinSum > 0) {
            coinLogService.awardCoins(userId, CoinType.ACHIEVEMENT_COIN, -coinSum,
                    CoinLogService.CoinEvent.LOG_DELETED_DEDUCTION.getDescription(), id);
        }

        evLogRepository.deleteById(id);
    }

    public List<EvLogResponse> getLogsForCar(UUID carId, UUID userId) {
        return getLogsForCar(carId, userId, null);
    }

    public List<EvLogResponse> getLogsForCar(UUID carId, UUID userId, Integer limit) {
        return getLogsForCar(carId, userId, limit, 0);
    }

    @Transactional(readOnly = true)
    public List<EvLogResponse> getLogsForCar(UUID carId, UUID userId, Integer limit, int page) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));

        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }

        // All logs sorted ascending — needed for consumption context (logX lookups)
        List<EvLog> allLogsSorted = evLogRepository.findAllByCarId(carId).stream()
                .sorted(Comparator.comparing(EvLog::getLoggedAt))
                .toList();

        // Load vehicle spec for spec-level charging efficiency override (nullable — car may not have a spec)
        VehicleSpecification spec = car.getVehicleSpecificationId() != null
                ? vehicleSpecificationRepository.findById(car.getVehicleSpecificationId()).orElse(null)
                : null;

        // Compute per-log consumption + plausibility on the full dataset (SoC-based).
        // absorbedLogIds: Teilladungen ohne Odometer (z.B. Spritmonitor), deren kWh in einem
        // Fensterwert aufgegangen sind — das Frontend erklärt damit den fehlenden Einzelwert.
        ConsumptionCalculationService.PerLogConsumptionResult perLog = car.getNominalNetCapacityKwh() != null
                ? calculationService.calculateConsumptionPerLogDetailed(allLogsSorted, calculationService.buildCapacityLookup(car), calculationService.lookupWltp(car), spec)
                : new ConsumptionCalculationService.PerLogConsumptionResult(Map.of(), Set.of());
        Map<UUID, ConsumptionResult> consumptionByLog = new LinkedHashMap<>(perLog.byLogId());

        // Distance since last charge — covers logs with odometer regardless of SoC availability
        Map<UUID, Integer> distanceByLogId = calculationService.computeDistanceByLogId(allLogsSorted);

        // Fallback: for logs with distance but no SoC-based consumption, estimate via kWh_charged/distance.
        // Marked as estimated=true so the frontend can display it differently (e.g. "~16.35 kWh/100km").
        // Less accurate than SoC-based (kWh_charged ≠ kWh_consumed), but useful when no SoC is available.
        for (EvLog log : allLogsSorted) {
            if (consumptionByLog.containsKey(log.getId())) continue;
            Integer dist = distanceByLogId.get(log.getId());
            if (dist == null || dist < plausibility.getMinTripDistanceKm()) continue;
            if (!log.hasEnergyData()) continue;
            double c = calculationService.effectiveKwhForConsumption(log, spec).doubleValue() / dist * 100.0;
            boolean plausible = c >= plausibility.getAbsoluteMinKwhPer100km() && c <= plausibility.getAbsoluteMaxKwhPer100km();
            consumptionByLog.put(log.getId(), new ConsumptionResult(
                    BigDecimal.valueOf(c).setScale(2, RoundingMode.HALF_UP), plausible, dist, CalculationQuality.KWH_ESTIMATED));
        }

        // Return the requested page, enriched with consumption and distance data.
        List<EvLog> page_logs = (limit != null && limit > 0)
                ? evLogRepository.findLatestByCarId(carId, limit, page)
                : allLogsSorted.reversed();

        return page_logs.stream()
                .map(log -> EvLogResponse.fromDomain(log, consumptionByLog.get(log.getId()), distanceByLogId.get(log.getId()),
                        perLog.absorbedLogIds().contains(log.getId()) ? Boolean.TRUE : null))
                .toList();
    }

    /**
     * Toggles include_in_statistics for a single log. Ownership is verified.
     */
    @Transactional
    public EvLogResponse updateIncludeInStatistics(UUID id, UUID userId, boolean includeInStatistics) {
        EvLog log = evLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Log not found"));
        Car car = carRepository.findById(log.getCarId())
                .orElseThrow(() -> new IllegalArgumentException("Associated car not found"));
        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Log not found for current user (ownership mismatch).");
        }
        EvLog updated = log.withIncludeInStatistics(includeInStatistics);
        EvLog saved = evLogRepository.save(updated);
        return EvLogResponse.fromDomain(saved);
    }

    /**
     * Weist einen einzelnen (ungruppierten) Log einem anderen Fahrzeug zu.
     * Beide Fahrzeuge müssen dem gleichen User gehören.
     */
    @Transactional
    public void reassignLog(UUID logId, UUID targetCarId, UUID userId) {
        EvLog log = evLogRepository.findById(logId)
                .orElseThrow(() -> new IllegalArgumentException("Log not found"));

        Car sourceCar = carRepository.findById(log.getCarId())
                .orElseThrow(() -> new IllegalArgumentException("Source car not found"));
        if (!sourceCar.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own this log");
        }

        Car targetCar = carRepository.findById(targetCarId)
                .orElseThrow(() -> new IllegalArgumentException("Target car not found"));
        if (!targetCar.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the target car");
        }

        evLogRepository.updateCarIdForLog(logId, targetCarId);
    }

    /**
     * Führt zwei Ladevorgänge desselben Autos zu einem vollständigen Eintrag zusammen.
     * Typischer Use-Case: Wallbox-Log (kwhCharged) + Fahrzeug-Log (kwhAtVehicle + SoC).
     * Der Ziel-Log wird mit den fehlenden Werten des Quell-Logs ergänzt, der Quell-Log wird gelöscht.
     */
    @Transactional
    public EvLog mergeLog(UUID targetLogId, UUID sourceLogId, UUID userId, boolean preferSource) {
        if (targetLogId.equals(sourceLogId)) {
            throw new ConflictException("MERGE_SELF", "Cannot merge a log with itself");
        }
        EvLog target = evLogRepository.findById(targetLogId)
                .orElseThrow(() -> NotFoundException.forEntity("Target log", targetLogId));
        EvLog source = evLogRepository.findById(sourceLogId)
                .orElseThrow(() -> NotFoundException.forEntity("Source log", sourceLogId));

        Car targetCar = carRepository.findById(target.getCarId())
                .orElseThrow(() -> NotFoundException.forEntity("Target car", target.getCarId()));
        if (!targetCar.getUserId().equals(userId)) {
            throw ForbiddenException.notOwner("log", targetLogId);
        }
        Car sourceCar = carRepository.findById(source.getCarId())
                .orElseThrow(() -> NotFoundException.forEntity("Source car", source.getCarId()));
        if (!sourceCar.getUserId().equals(userId)) {
            throw ForbiddenException.notOwner("log", sourceLogId);
        }
        if (!target.getCarId().equals(source.getCarId())) {
            throw new ConflictException("MERGE_DIFFERENT_CARS", "Both logs must belong to the same car");
        }
        if (Duration.between(target.getLoggedAt(), source.getLoggedAt()).abs().compareTo(MERGE_WINDOW) > 0) {
            throw new ConflictException("MERGE_WINDOW_EXCEEDED",
                    "Both logs must be within " + MERGE_WINDOW.toHours() + "h of each other");
        }

        EvLog primary = preferSource ? source : target;
        EvLog fallback = preferSource ? target : source;

        BigDecimal mergedKwhCharged = primary.getKwhCharged() != null ? primary.getKwhCharged() : fallback.getKwhCharged();
        BigDecimal mergedKwhAtVehicle = primary.getKwhAtVehicle() != null ? primary.getKwhAtVehicle() : fallback.getKwhAtVehicle();
        BigDecimal mergedSocStart = primary.getSocBeforeChargePercent() != null ? primary.getSocBeforeChargePercent() : fallback.getSocBeforeChargePercent();
        BigDecimal mergedSocEnd = primary.getSocAfterChargePercent() != null ? primary.getSocAfterChargePercent() : fallback.getSocAfterChargePercent();
        Integer mergedOdometer = primary.getOdometerKm() != null ? primary.getOdometerKm() : fallback.getOdometerKm();
        Integer mergedDuration = primary.getChargeDurationMinutes() != null ? primary.getChargeDurationMinutes() : fallback.getChargeDurationMinutes();
        BigDecimal mergedCost = primary.getCostEur() != null ? primary.getCostEur() : fallback.getCostEur();
        String mergedGeohash = primary.getGeohash() != null ? primary.getGeohash() : fallback.getGeohash();

        // Wenn beide Felder nach dem Merge gesetzt sind: AT_CHARGER (kwhCharged ist leitend)
        EnergyMeasurementType mergedMeasurementType = (mergedKwhCharged != null && mergedKwhAtVehicle != null)
                ? EnergyMeasurementType.AT_CHARGER
                : (mergedKwhCharged != null ? EnergyMeasurementType.AT_CHARGER : EnergyMeasurementType.AT_VEHICLE);

        EvLog merged = target.toBuilder()
                .kwhCharged(mergedKwhCharged)
                .kwhAtVehicle(mergedKwhAtVehicle)
                .socBeforeChargePercent(mergedSocStart)
                .socAfterChargePercent(mergedSocEnd)
                .odometerKm(mergedOdometer)
                .chargeDurationMinutes(mergedDuration)
                .costEur(mergedCost)
                .geohash(mergedGeohash)
                .measurementType(mergedMeasurementType)
                .build();

        EvLog saved = evLogRepository.save(merged);
        evLogRepository.deleteById(sourceLogId);

        if (mergedKwhAtVehicle != null) {
            eventPublisher.publishEvent(new SohAutoDetectEvent(targetCar));
        }
        return saved;
    }

}