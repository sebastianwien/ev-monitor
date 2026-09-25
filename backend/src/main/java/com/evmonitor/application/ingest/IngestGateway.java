package com.evmonitor.application.ingest;

import com.evmonitor.application.CoinLogService;
import com.evmonitor.application.EvLogWriter;
import com.evmonitor.application.InternalTripRequest;
import com.evmonitor.application.LocationPricing;
import com.evmonitor.application.MissedStartSocEstimator;
import com.evmonitor.application.SohAutoDetectEvent;
import com.evmonitor.application.ingest.event.ImportEventErrors;
import com.evmonitor.application.ingest.event.ImportEventOutcome;
import com.evmonitor.application.ingest.event.ImportEventRecorder;
import com.evmonitor.domain.*;
import com.evmonitor.domain.exception.ForbiddenException;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.domain.exception.ValidationException;
import com.evmonitor.domain.route.RouteSketcher;
import com.evmonitor.domain.weather.TemperatureEnricher;
import com.evmonitor.infrastructure.persistence.ingest.ImportEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Die eine Tür, durch die Importe Ladungen und Fahrten anlegen (Herstellerarchitektur R2). Prüft
 * Ownership, wendet die {@link IngestPolicy} der Quelle an, dedupliziert (Tombstones zählen mit),
 * legt an, bepreist, vergibt Watt und hängt Extras wie Ladekurven an.
 *
 * <p>Die alten Einstiege ({@code PublicApiImportService.importSessions},
 * {@code EvLogService.createInternalLog}, {@code TripService.saveTrip}) parsen nur noch ihr Format
 * und rufen hierher. Unterschiede zwischen ihnen stehen in {@link IngestPolicies}, nicht im Code.
 *
 * <p>Jeder Aufruf hinterlässt eine Zeile Import-Protokoll ({@link ImportEventRecorder}), auch wenn er
 * abgelehnt wird oder scheitert. Rückgaben und Exceptions bleiben davon unberührt.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestGateway {

    private final CarRepository carRepository;
    private final EvLogRepository evLogRepository;
    private final EvLogWriter evLogWriter;
    private final LocationPricing locationPricing;
    private final CoinLogService coinLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final EvTripRepository tripRepository;
    private final TemperatureEnricher temperatureEnricher;
    private final RouteSketcher routeSketcher;
    private final ImportEventRecorder importEvents;

    /**
     * Legt die Ladungen eines Autos an. Ein Aufruf ist eine Transaktion: scheitert der Commit (z. B.
     * Unique-Constraint), rollt der ganze Batch zurück.
     *
     * @throws NotFoundException  Auto unbekannt
     * @throws ForbiddenException Auto gehört nicht dem Nutzer
     */
    @Transactional
    public IngestResult ingestCharging(IngestCommand command) {
        long started = System.nanoTime();
        try {
            IngestResult result = ingestChargingEntries(command);
            importEvents.record(ImportEvent.of(command.dataSource(), command.userId(), command.carId())
                    .outcome(ImportEventOutcome.of(result.imported(), result.errors()))
                    .sessionsImported(result.imported())
                    .sessionsSkipped(result.skipped())
                    .sessionsFailed(result.errors())
                    .durationMs(elapsedMs(started))
                    .build());
            return result;
        } catch (RuntimeException e) {
            // Unbekanntes Auto: car_id bleibt leer, sonst scheitert das Protokoll am Fremdschlüssel.
            UUID carId = e instanceof NotFoundException ? null : command.carId();
            importEvents.record(rejectedOrFailed(ImportEvent.of(command.dataSource(), command.userId(), carId), e, started));
            throw e;
        }
    }

    private IngestResult ingestChargingEntries(IngestCommand command) {
        Car car = requireOwnedCar(command.carId(), command.userId());
        IngestPolicy policy = IngestPolicies.forSource(command.dataSource(), command.door());

        // Chronologisch, damit der Bump bei mehreren Ladungen je Tag in Reihenfolge versetzt
        List<ChargingEntry> sorted = command.entries().stream()
                .sorted(Comparator.comparing(ChargingEntry::loggedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        int skipped = 0;
        int errors = 0;
        List<EvLog> created = new ArrayList<>();
        Set<LocalDateTime> batchUsedTimestamps = new HashSet<>();

        for (ChargingEntry entry : sorted) {
            if (!policy.isolateEntryErrors()) {
                EvLog saved = ingestOne(car, command, policy, entry, batchUsedTimestamps);
                if (saved == null) skipped++; else created.add(saved);
                continue;
            }
            try {
                EvLog saved = ingestOne(car, command, policy, entry, batchUsedTimestamps);
                if (saved == null) skipped++; else created.add(saved);
            } catch (Exception e) {
                log.warn("Ingest {}: Fehler beim Verarbeiten einer Ladung: {}", command.dataSource(), e.getMessage());
                errors++;
            }
        }

        return new IngestResult(created.size(), skipped, errors, List.copyOf(created));
    }

    /** @return die angelegte Ladung, {@code null} bei Duplikat */
    private EvLog ingestOne(Car car, IngestCommand command, IngestPolicy policy, ChargingEntry entry,
                            Set<LocalDateTime> batchUsedTimestamps) {
        LocalDateTime loggedAt = entry.loggedAt() != null ? entry.loggedAt() : LocalDateTime.now();

        // Mehrere Einträge mit demselben Zeitstempel (z. B. Tagesdatum-Importe mit mehreren Ladungen
        // je Tag) je 10 Minuten versetzen, damit sie im Feed unterscheidbar bleiben.
        if (policy.bumpSameTimestampInBatch()) {
            while (batchUsedTimestamps.contains(loggedAt)) {
                loggedAt = loggedAt.plusMinutes(10);
            }
            batchUsedTimestamps.add(loggedAt);
        }

        if (isDuplicate(car.getId(), loggedAt, command.dataSource(), policy.dedupWindow())) {
            return null;
        }

        EvLog evLog = toEvLog(car, command.dataSource(), policy, entry, loggedAt);
        if (policy.inheritTireAndRouteType()) {
            evLog = inheritTireAndRouteType(evLog);
        }
        evLog = locationPricing.enrich(evLog, command.userId());

        EvLog saved = evLogWriter.save(evLog);

        // Kurven-Snapshot als JSONB neben dem Log: überlebt die Retention der Rohsignale.
        if (entry.socCurvePointsJson() != null && !entry.socCurvePointsJson().isBlank()) {
            evLogRepository.updateSocCurvePoints(saved.getId(), entry.socCurvePointsJson());
        }
        if (entry.powerCurvePointsJson() != null && !entry.powerCurvePointsJson().isBlank()) {
            evLogRepository.updatePowerCurvePoints(saved.getId(), entry.powerCurvePointsJson());
        }

        if (policy.coinEvent() != null) {
            coinLogService.awardCoinsForEvent(command.userId(), policy.coinEvent(), saved.getId());
        }

        // save() stößt die SoH-Erkennung nur mit kwhAtVehicle an; Live-Quellen liefern oft nur kwhCharged.
        if (policy.sohEventWithoutVehicleKwh() && saved.getKwhAtVehicle() == null) {
            eventPublisher.publishEvent(new SohAutoDetectEvent(car));
        }

        return saved;
    }

    /** Dedup je Auto und Quelle auf die Minute, mit Toleranzfenster laut Policy. Sieht Tombstones. */
    private boolean isDuplicate(UUID carId, LocalDateTime loggedAt, DataSource dataSource, Duration window) {
        LocalDateTime minute = loggedAt.withSecond(0).withNano(0);
        if (!window.isZero()) {
            return evLogRepository.existsByCarIdAndDataSourceAndLoggedAtBetween(
                    carId, dataSource, minute.minus(window), minute.plus(window));
        }
        return evLogRepository.existsByCarIdAndLoggedAtAndDataSource(carId, minute, dataSource);
    }

    private EvLog toEvLog(Car car, DataSource dataSource, IngestPolicy policy, ChargingEntry entry,
                          LocalDateTime loggedAt) {
        BigDecimal kwhCharged = entry.kwhCharged();
        BigDecimal kwhAtVehicle = entry.kwhAtVehicle();
        EnergyMeasurementType measurementType = entry.measurementType();
        // Fahrzeugseitige kWh im kWh-Feld nach kwhAtVehicle umhängen, sonst rechnet die
        // Verbrauchsformel einen Ladeverlust auf einen Wert, der ihn schon nicht mehr enthält.
        if (policy.kwhIsVehicleSide() && kwhCharged != null && kwhAtVehicle == null) {
            kwhAtVehicle = kwhCharged;
            kwhCharged = null;
            measurementType = EnergyMeasurementType.AT_VEHICLE;
        }

        // R15: verpasster Ladestart - socBefore aus den vollständigen Ladungen desselben Autos herleiten.
        BigDecimal socBefore = entry.socBefore();
        if (Boolean.TRUE.equals(entry.socStartMissed()) && socBefore == null) {
            socBefore = deriveMissedStartSoc(car, entry);
        }

        LocalDateTime now = LocalDateTime.now();
        return EvLog.builder()
                .id(UUID.randomUUID())
                .carId(car.getId())
                .kwhCharged(kwhCharged)
                .kwhAtVehicle(kwhAtVehicle)
                .measurementType(measurementType)
                .energySource(entry.energySource())
                .costEur(entry.costEur())
                .pricePerKwh(entry.pricePerKwh())
                .chargeDurationMinutes(entry.chargeDurationMinutes())
                .geohash(entry.geohash())
                .publicCharging(entry.publicCharging())
                .cpoName(entry.cpoName())
                .odometerKm(entry.odometerKm())
                .odometerSuggestionMinKm(entry.odometerSuggestionMinKm())
                .odometerSuggestionMaxKm(entry.odometerSuggestionMaxKm())
                .maxChargingPowerKw(entry.maxChargingPowerKw())
                .socBeforeChargePercent(socBefore)
                .socAfterChargePercent(entry.socAfter())
                .loggedAt(loggedAt)
                .dataSource(dataSource)
                .includeInStatistics(dataSource.includeInStatistics())
                .chargingType(entry.chargingType())
                .routeType(entry.routeType())
                .tireType(entry.tireType())
                .temperatureCelsius(entry.temperatureCelsius())
                .rawImportData(entry.rawImportData())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * R15: median kWh je SoC-Punkt aus den vollständigen Ladungen des Autos, Fallback
     * SoH-bereinigte Kapazität. Rein rechnend - siehe {@link MissedStartSocEstimator}.
     */
    private BigDecimal deriveMissedStartSoc(Car car, ChargingEntry entry) {
        List<MissedStartSocEstimator.Charge> cleanCharges = evLogRepository
                .findRecentAtVehicleLogsWithSoc(car.getId(), 40).stream()
                .map(l -> new MissedStartSocEstimator.Charge(
                        l.getSocBeforeChargePercent(), l.getSocAfterChargePercent(), l.getKwhAtVehicle()))
                .toList();
        return MissedStartSocEstimator.estimateSocStart(
                entry.socAfter(), entry.kwhCharged(), cleanCharges, car.getEffectiveBatteryCapacityKwh());
    }

    /**
     * Reifen und Strecke vom letzten Log vor diesem übernehmen. Live-Quellen (Tesla, Wallbox,
     * Smartcar) tragen die Angaben nicht; ohne Erben kippt die "letzten Wert übernehmen"-UX still
     * auf NULL/SUMMER. "Vor loggedAt", damit spät eintreffende Telemetrie den damals gültigen Wert erbt.
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

    private Car requireOwnedCar(UUID carId, UUID userId) {
        Car car = carRepository.findById(carId).orElseThrow(() -> NotFoundException.forEntity("Car", carId));
        if (!car.isOwnedBy(userId)) {
            throw ForbiddenException.notOwner("Car", carId);
        }
        return car;
    }

    /**
     * Legt eine abgeschlossene Fahrt an. Dedup über {@code externalId} global, auch gegen
     * gelöschte Fahrten: vom Nutzer gelöscht heißt gelöscht, der Sync legt sie nicht neu an.
     *
     * @return id der angelegten oder schon vorhandenen Fahrt und ob sie neu ist
     * @throws NotFoundException  Auto unbekannt oder gelöscht
     * @throws ForbiddenException Auto gehört nicht dem Nutzer
     */
    @Transactional
    public TripIngest ingestTrip(InternalTripRequest req) {
        long started = System.nanoTime();
        try {
            TripIngest ingest = ingestTripOnce(req);
            importEvents.record(tripEvent(req)
                    .outcome(ingest.created() ? ImportEventOutcome.IMPORTED : ImportEventOutcome.NO_NEW_DATA)
                    .tripsImported(ingest.created() ? 1 : 0)
                    .tripsSkipped(ingest.created() ? 0 : 1)
                    .durationMs(elapsedMs(started))
                    .build());
            return ingest;
        } catch (RuntimeException e) {
            importEvents.record(rejectedOrFailed(tripEvent(req), e, started));
            throw e;
        }
    }

    /** @param created false = schon vorhanden (auch gelöscht), {@code id} ist dann die vorhandene Fahrt */
    public record TripIngest(UUID id, boolean created) {}

    private TripIngest ingestTripOnce(InternalTripRequest req) {
        if (req.userId() == null) {
            throw new ValidationException("userId is required");
        }
        if (req.carId() == null) {
            throw new ValidationException("carId is required");
        }
        // Vor der Dedup: sonst verriete eine bekannte externalId die id einer fremden Fahrt.
        Car car = requireOwnedCar(req.carId(), req.userId());
        if (req.externalId() != null) {
            var existing = tripRepository.findByExternalId(req.externalId());
            if (existing.isPresent()) {
                log.debug("Trip with externalId={} already exists (deleted={}) - skipping",
                        req.externalId(), existing.get().getDeletedAt() != null);
                return new TripIngest(existing.get().getId(), false);
            }
        }

        EvTrip trip = EvTrip.builder()
                .externalId(req.externalId())
                .carId(req.carId())
                .userId(req.userId())
                .dataSource(req.dataSource())
                .tripStartedAt(req.tripStartedAt())
                .tripEndedAt(req.tripEndedAt())
                .socStart(req.socStart())
                .socEnd(req.socEnd())
                .odometerStartKm(req.odometerStartKm())
                .odometerEndKm(req.odometerEndKm())
                .distanceKm(req.distanceKm())
                .locationStartGeohash(req.locationStartGeohash())
                .locationEndGeohash(req.locationEndGeohash())
                .outsideTempCelsius(req.outsideTempCelsius())
                .energyRemainingStartKwh(req.energyRemainingStartKwh())
                .energyRemainingEndKwh(req.energyRemainingEndKwh())
                .estimatedConsumedKwh(req.estimatedConsumedKwh())
                .avgSpeedKmh(req.avgSpeedKmh())
                .maxSpeedKmh(req.maxSpeedKmh())
                .status(req.status() != null ? req.status() : "COMPLETED")
                .rawPayload(req.rawPayload())
                .telemetryExtras(req.telemetryExtras())
                .tracePolyline(req.tracePolyline())
                .userCreated(false)
                .build();

        if (trip.getEstimatedConsumedKwh() == null) {
            trip.setEstimatedConsumedKwh(EvTrip.estimateConsumedKwh(
                    req.socStart(), req.socEnd(), () -> Optional.of(car)));
        }

        EvTrip saved = tripRepository.save(trip);
        log.info("Trip saved: id={} externalId={} car={} distance={} km",
                saved.getId(), req.externalId(), req.carId(), req.distanceKm());

        if (req.outsideTempCelsius() == null
                && req.tripStartedAt() != null
                && (req.locationStartGeohash() != null || req.locationEndGeohash() != null)) {
            final UUID tripId = saved.getId();
            final String startGeohash = req.locationStartGeohash();
            final String endGeohash = req.locationEndGeohash();
            final LocalDateTime startedAt = req.tripStartedAt().toLocalDateTime();
            final LocalDateTime endedAt = req.tripEndedAt() != null ? req.tripEndedAt().toLocalDateTime() : null;
            afterCommit(() -> temperatureEnricher.enrichTrip(tripId, startGeohash, endGeohash, startedAt, endedAt));
        }

        // Die Linie hängt an denselben Geohashes wie die Temperatur, wird aber unabhängig davon
        // geholt - eine Fahrt kann ihre Temperatur schon mitbringen und trotzdem eine Route brauchen.
        // Bringt die Fahrt ihre gefahrene Spur mit, bekommt der Router sie statt der beiden Enden:
        // gefahren schlägt gerechnet. Nach dem Commit, damit der Trip in der DB steht.
        final UUID tripIdForRoute = saved.getId();
        if (req.tracePolyline() != null) {
            final String trace = req.tracePolyline();
            final BigDecimal drivenKm = trip.getDistanceKm();
            afterCommit(() -> routeSketcher.matchTrace(tripIdForRoute, trace, drivenKm));
        } else if (req.locationStartGeohash() != null && req.locationEndGeohash() != null) {
            final String routeStart = req.locationStartGeohash();
            final String routeEnd = req.locationEndGeohash();
            afterCommit(() -> routeSketcher.sketchTrip(tripIdForRoute, routeStart, routeEnd));
        }

        return new TripIngest(saved.getId(), true);
    }

    /** Fahrten melden ihre Quelle als Text; eine unbekannte wird trotzdem protokolliert. */
    private static ImportEvent.ImportEventBuilder tripEvent(InternalTripRequest req) {
        try {
            return ImportEvent.of(DataSource.valueOf(req.dataSource()), req.userId(), req.carId());
        } catch (IllegalArgumentException | NullPointerException e) {
            return ImportEvent.ofUnknownSource(req.dataSource(), req.userId(), req.carId());
        }
    }

    /** Abgelehnt, wenn die Anfrage selbst nicht passt (Auto, Besitz, Pflichtfeld), sonst gescheitert. */
    private static ImportEvent rejectedOrFailed(ImportEvent.ImportEventBuilder event, RuntimeException e, long started) {
        boolean rejected = e instanceof NotFoundException || e instanceof ForbiddenException
                || e instanceof ValidationException;
        return event
                .outcome(rejected ? ImportEventOutcome.REJECTED : ImportEventOutcome.FAILED)
                .error(ImportEventErrors.describe(e))
                .durationMs(elapsedMs(started))
                .build();
    }

    private static int elapsedMs(long startedNanos) {
        return (int) Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    private static void afterCommit(Runnable task) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }
}
