package com.evmonitor.application;

import com.evmonitor.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Einzige Quelle für den Fahrverbrauch (Reichweite, WLTP-Vergleich, Peer beim Fahren), so wie
 * {@code EvLogService} für den Verbrauch aus Ladungen. Rechnet über {@code ev_trip}, die Regeln je
 * Fahrt und Auto stehen in {@link DriveConsumption}, die Auswahl der Reichweite in {@link RangeBasisPolicy}.
 *
 * <p>Zählen nur abgeschlossene Fahrten aus geprüften Quellen ({@link #SOURCES}), die der Halter sehen darf:
 * Live-Fahrten nur bei freigegebenen Modellen ({@link TripDetectionEligibility}) und mit Berechtigung.
 * Smartcar und abgeleitete Tesla-Fahrten zählen nicht.
 *
 * <p>Der Modellschnitt liest alle Fahrten aller Autos eines Modells und ist je Modell und Zeitraum 24 h
 * gecacht ({@link #MODEL_CACHE}), mit Summen je Auto; der Fragende fällt beim Lesen heraus.
 */
@Service
@RequiredArgsConstructor
public class DriveConsumptionService {

    public static final String MODEL_CACHE = "driveConsumptionModel";

    static final Set<String> SOURCES = Set.of(
            "TESLA_LIVE", "TESSIE", "XPENG_IMPORT", "POLESTAR_DATA_PORTAL", "API_UPLOAD", "USER_CREATED");

    private static final OffsetDateTime ALL_FROM = Instant.EPOCH.atOffset(ZoneOffset.UTC);
    private static final OffsetDateTime ALL_TO = OffsetDateTime.parse("9999-12-31T23:59:59Z");
    private static final Predicate<OffsetDateTime> SUMMER = t -> t.getMonthValue() >= 5 && t.getMonthValue() <= 8;
    private static final Predicate<OffsetDateTime> WINTER = t -> t.getMonthValue() >= 11 || t.getMonthValue() <= 2;

    private final EvTripRepository tripRepository;
    private final CarRepository carRepository;
    private final UserRepository userRepository;
    private final CacheManager cacheManager;

    /** "drive" (Standard) oder "charges": setzt die Reichweite ohne Deploy auf den Verbrauch aus Ladungen zurück. */
    @Value("${evmonitor.range.basis:drive}")
    private String rangeBasisMode = "drive";

    /** Zeitraum der Statistik, Tage einschließlich; null heißt offen. Wie die Ladungen in UTC. */
    public record Period(LocalDate start, LocalDate end) {
        boolean bounded() { return start != null && end != null; }
        OffsetDateTime from() { return start == null ? ALL_FROM : start.atStartOfDay().atOffset(ZoneOffset.UTC); }
        OffsetDateTime to() { return end == null ? ALL_TO : end.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC); }
    }

    /**
     * Fahrverbrauch eines eigenen Autos für die Statistik. Ownership prüft der Aufrufer
     * ({@code EvLogStatisticsService.getStatistics}).
     *
     * @param chargesConsumption Verbrauch aus Ladungen im selben Zeitraum, Rückfall der Reichweite
     */
    @Transactional(readOnly = true)
    public DriveStatistics forStatistics(Car car, User owner, Period period, BigDecimal chargesConsumption) {
        BigDecimal capacity = car.getEffectiveBatteryCapacityKwh();
        List<DriveTripSample> all = visibleSamples(car, owner, ALL_FROM, ALL_TO);
        OffsetDateTime from = period.from(), to = period.to();
        BigDecimal own = DriveConsumption.of(filter(all, t -> !t.isBefore(from) && t.isBefore(to)), capacity).kwhPer100km();
        BigDecimal summer = DriveConsumption.of(filter(all, SUMMER), capacity).kwhPer100km();
        BigDecimal winter = DriveConsumption.of(filter(all, WINTER), capacity).kwhPer100km();
        if (!period.bounded()) {
            return new DriveStatistics(own, summer, winter, null, 0, null, List.of());
        }
        List<DriveStatistics.MonthDrive> months = byMonth(filter(all, t -> !t.isBefore(from) && t.isBefore(to)), capacity);
        ModelDriveAverage model = car.getModel() == null ? ModelDriveAverage.NONE
                : ModelDriveAverage.of(modelCars(car.getModel(), period), owner.getId());
        RangeBasis basis = RangeBasisPolicy.decide(own, model.consumptionKwhPer100km(), chargesConsumption,
                !"charges".equalsIgnoreCase(rangeBasisMode));
        return new DriveStatistics(own, summer, winter, model.consumptionKwhPer100km(), model.users(), basis, months);
    }

    /** Je Kalendermonat dieselben Regeln wie für den Zeitraum; Monate ohne verlässlichen Wert fehlen. */
    private static List<DriveStatistics.MonthDrive> byMonth(List<DriveTripSample> trips, BigDecimal capacity) {
        Map<YearMonth, List<DriveTripSample>> grouped = trips.stream()
                .collect(Collectors.groupingBy(t -> YearMonth.from(t.startedAt().withOffsetSameInstant(ZoneOffset.UTC)),
                        TreeMap::new, Collectors.toList()));
        List<DriveStatistics.MonthDrive> result = new ArrayList<>();
        grouped.forEach((month, list) -> {
            BigDecimal value = DriveConsumption.of(list, capacity).kwhPer100km();
            if (value != null) result.add(new DriveStatistics.MonthDrive(month.toString(), value));
        });
        return result;
    }

    /** Winter-Fahrverbrauch (November bis Februar, alle Jahre) anderer Fahrer desselben Modells, ab 5 Nutzern. */
    @Transactional(readOnly = true)
    public BigDecimal modelWinterConsumption(Car car, UUID excludedUserId) {
        if (car.getModel() == null) return null;
        return ModelDriveAverage.of(modelCars(car.getModel(), new Period(null, null), WINTER), excludedUserId)
                .consumptionKwhPer100km();
    }

    void setRangeBasisMode(String mode) {
        this.rangeBasisMode = mode;
    }

    private record ModelKey(CarBrand.CarModel model, LocalDate start, LocalDate end, boolean winterOnly) {}

    private List<ModelDriveAverage.CarDrive> modelCars(CarBrand.CarModel model, Period period) {
        return modelCars(model, period, t -> true);
    }

    private List<ModelDriveAverage.CarDrive> modelCars(CarBrand.CarModel model, Period period, Predicate<OffsetDateTime> season) {
        ModelKey key = new ModelKey(model, period.start(), period.end(), season == WINTER);
        Cache cache = cacheManager.getCache(MODEL_CACHE);
        if (cache == null) return computeModelCars(model, period, season);
        return cache.get(key, () -> computeModelCars(model, period, season));
    }

    private List<ModelDriveAverage.CarDrive> computeModelCars(CarBrand.CarModel model, Period period, Predicate<OffsetDateTime> season) {
        // Anonymisierte Autos haben keinen Halter mehr und zählen nicht
        List<Car> cars = carRepository.findAllByModel(model).stream().filter(c -> c.getUserId() != null).toList();
        if (cars.isEmpty()) return List.of();
        Map<UUID, User> owners = userRepository.findAllByIds(cars.stream().map(Car::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<UUID, Car> byId = new HashMap<>();
        for (Car c : cars) {
            User owner = owners.get(c.getUserId());
            if (owner != null && !owner.isSeedData()) byId.put(c.getId(), c);
        }
        if (byId.isEmpty()) return List.of();
        Map<UUID, List<DriveTripSample>> tripsByCar = tripRepository.findDriveSamples(
                        List.copyOf(byId.keySet()), SOURCES, DriveConsumption.MIN_TRIP_KM, period.from(), period.to()).stream()
                .filter(t -> season.test(t.startedAt()))
                .collect(Collectors.groupingBy(DriveTripSample::carId));
        List<ModelDriveAverage.CarDrive> result = new ArrayList<>();
        tripsByCar.forEach((carId, trips) -> {
            Car c = byId.get(carId);
            User owner = owners.get(c.getUserId());
            List<DriveTripSample> visible = trips.stream().filter(t -> isVisible(t, c, owner)).toList();
            result.add(new ModelDriveAverage.CarDrive(c.getUserId(), DriveConsumption.of(visible, c.getEffectiveBatteryCapacityKwh())));
        });
        return List.copyOf(result);
    }

    private List<DriveTripSample> visibleSamples(Car car, User owner, OffsetDateTime from, OffsetDateTime to) {
        return tripRepository.findDriveSamples(List.of(car.getId()), SOURCES, DriveConsumption.MIN_TRIP_KM, from, to).stream()
                .filter(t -> isVisible(t, car, owner))
                .toList();
    }

    /** Wie die Fahrtenliste des Halters, aber ohne Admin-Ausnahme: Live-Fahrten nur bei freigegebenen Modellen. */
    private static boolean isVisible(DriveTripSample t, Car car, User owner) {
        if (t.userId() == null || !car.isOwnedBy(t.userId())) return false;
        if (!EvTrip.LIVE_TRIP_SOURCES.contains(t.dataSource())) return true;
        if (car.getModel() == null) return false;
        return owner.canViewLiveTrips(car.getModel().getBrand()) && TripDetectionEligibility.isEligible(car);
    }

    private static List<DriveTripSample> filter(List<DriveTripSample> trips, Predicate<OffsetDateTime> when) {
        return trips.stream().filter(t -> when.test(t.startedAt())).toList();
    }
}
