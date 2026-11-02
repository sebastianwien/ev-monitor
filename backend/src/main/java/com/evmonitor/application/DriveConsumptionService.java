package com.evmonitor.application;

import com.evmonitor.domain.*;
import lombok.RequiredArgsConstructor;
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
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Einzige Quelle für den Fahrverbrauch (Reichweite, WLTP-Vergleich, Peer beim Fahren), so wie
 * {@code EvLogService} für den Verbrauch aus Ladungen. Rechnet über {@code ev_trip}, die Regeln je
 * Fahrt und Auto stehen in {@link DriveConsumption}; welche Quelle die Reichweite bestimmt, entscheidet
 * {@link RangeConsumptionPolicy} im {@code RangeEstimateService}.
 *
 * <p>Zählen nur abgeschlossene Fahrten aus geprüften Quellen ({@link #SOURCES}), die der Halter sehen darf:
 * Live-Fahrten nur bei freigegebenen Modellen ({@link TripDetectionEligibility}) und mit Berechtigung.
 * Smartcar und abgeleitete Tesla-Fahrten zählen nicht.
 *
 * <p>Der Schnitt anderer Fahrer liest alle Fahrten aller Autos einer Variante oder eines Modells einmal
 * je Gruppe und Zeitraum und hält sie 24 h ({@link #MODEL_CACHE}) je Auto; Saisons und der Ausschluss des
 * Fragenden entstehen beim Lesen.
 */
@Service
@RequiredArgsConstructor
public class DriveConsumptionService {

    public static final String MODEL_CACHE = "driveConsumptionModel";

    static final Set<String> SOURCES = Set.of(
            "TESLA_LIVE", "TESSIE", "XPENG_IMPORT", "POLESTAR_DATA_PORTAL", "API_UPLOAD", "USER_CREATED");

    private static final OffsetDateTime ALL_FROM = Instant.EPOCH.atOffset(ZoneOffset.UTC);
    private static final OffsetDateTime ALL_TO = OffsetDateTime.parse("9999-12-31T23:59:59Z");

    /** Saisons in UTC wie die Monatswerte und die Ladungen, unabhängig vom Offset, den die DB liefert. */
    public enum Season {
        ALL, SUMMER, WINTER;

        boolean test(OffsetDateTime t) {
            int m = t.withOffsetSameInstant(ZoneOffset.UTC).getMonthValue();
            return switch (this) {
                case ALL -> true;
                case SUMMER -> m >= 5 && m <= 8;
                case WINTER -> m >= 11 || m <= 2;
            };
        }
    }

    private final EvTripRepository tripRepository;
    private final CarRepository carRepository;
    private final UserRepository userRepository;
    private final CacheManager cacheManager;

    /** Zeitraum der Statistik, Tage einschließlich; null heißt offen. Wie die Ladungen in UTC. */
    public record Period(LocalDate start, LocalDate end) {
        public static final Period ALL = new Period(null, null);
        boolean bounded() { return start != null && end != null; }
        OffsetDateTime from() { return start == null ? ALL_FROM : start.atStartOfDay().atOffset(ZoneOffset.UTC); }
        OffsetDateTime to() { return end == null ? ALL_TO : end.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC); }
    }

    /**
     * Fahrverbrauch anderer Fahrer: welche Gruppe, welcher Zeitraum, welche Saison, wer nicht mitzählt.
     *
     * @param vehicleSpecificationId Variante des Fragenden, null ohne Spec (dann nur das Modell)
     * @param excludedUserId         der Fragende; null auf öffentlichen Seiten
     */
    public record PeerQuery(CarBrand.CarModel model, UUID vehicleSpecificationId, Period period, Season season, UUID excludedUserId) {}

    /**
     * Fahrverbrauch eines eigenen Autos für die Statistik. Ownership prüft der Aufrufer
     * ({@code EvLogStatisticsService.getStatistics}).
     */
    @Transactional(readOnly = true)
    public DriveStatistics forStatistics(Car car, User owner, Period period) {
        BigDecimal capacity = car.getEffectiveBatteryCapacityKwh();
        List<DriveTripSample> all = visibleSamples(car, owner, ALL_FROM, ALL_TO);
        OffsetDateTime from = period.from(), to = period.to();
        List<DriveTripSample> inPeriod = filter(all, t -> !t.isBefore(from) && t.isBefore(to));
        BigDecimal own = DriveConsumption.of(inPeriod, capacity).kwhPer100km();
        BigDecimal summer = DriveConsumption.of(filter(all, Season.SUMMER::test), capacity).kwhPer100km();
        BigDecimal winter = DriveConsumption.of(filter(all, Season.WINTER::test), capacity).kwhPer100km();
        if (!period.bounded()) {
            return new DriveStatistics(own, summer, winter, null, 0, List.of());
        }
        // Peer-Kachel: andere Fahrer desselben Modells im selben Zeitraum, über alle Varianten
        ModelDriveAverage model = car.getModel() == null ? ModelDriveAverage.NONE
                : ModelDriveAverage.of(modelCars(car.getModel(), period, Season.ALL), owner.getId());
        return new DriveStatistics(own, summer, winter, model.consumptionKwhPer100km(), model.users(), byMonth(inPeriod, capacity));
    }

    /** Fahrverbrauch anderer Fahrer, Variante vor Modell, je ab {@link ModelDriveAverage#MIN_USERS} anderen Fahrern. */
    @Transactional(readOnly = true)
    public PeerDrive peerDrive(PeerQuery q) {
        if (q.model() == null) return PeerDrive.NONE;
        ModelDriveAverage variant = q.vehicleSpecificationId() == null ? null
                : ModelDriveAverage.of(variantCars(q.vehicleSpecificationId(), q.period(), q.season()), q.excludedUserId());
        ModelDriveAverage model = ModelDriveAverage.of(modelCars(q.model(), q.period(), q.season()), q.excludedUserId());
        return PeerDrive.of(variant, model);
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

    /** Schlüssel im {@link #MODEL_CACHE}: Gruppe ("model:ID_3" oder "spec:<uuid>") und Zeitraum; Saisons entstehen im Speicher. */
    private record GroupKey(String group, LocalDate start, LocalDate end) {}

    /** Sichtbare Fahrten eines Autos der Gruppe mit dem, was der Schnitt braucht; der Cache hält sie je Gruppe und Zeitraum. */
    private record CarSamples(UUID userId, BigDecimal effectiveCapacityKwh, List<DriveTripSample> trips) {}

    private List<ModelDriveAverage.CarDrive> modelCars(CarBrand.CarModel model, Period period, Season season) {
        return cars(new GroupKey("model:" + model.name(), period.start(), period.end()),
                () -> carRepository.findAllByModel(model), period, season);
    }

    private List<ModelDriveAverage.CarDrive> variantCars(UUID specId, Period period, Season season) {
        return cars(new GroupKey("spec:" + specId, period.start(), period.end()),
                () -> carRepository.findAllByVehicleSpecificationId(specId), period, season);
    }

    /** Eine Fahrtenabfrage je Gruppe und Zeitraum, aus der alle drei Saisons gebildet werden. */
    private List<ModelDriveAverage.CarDrive> cars(GroupKey key, Supplier<List<Car>> cars, Period period, Season season) {
        Cache cache = cacheManager.getCache(MODEL_CACHE);
        List<CarSamples> samples = cache == null ? loadSamples(cars.get(), period)
                : cache.get(key, () -> loadSamples(cars.get(), period));
        return samples.stream()
                .map(c -> new ModelDriveAverage.CarDrive(c.userId(),
                        DriveConsumption.of(filter(c.trips(), season::test), c.effectiveCapacityKwh())))
                .toList();
    }

    private List<CarSamples> loadSamples(List<Car> group, Period period) {
        // Anonymisierte Autos haben keinen Halter mehr und zählen nicht
        List<Car> cars = group.stream().filter(c -> c.getUserId() != null).toList();
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
                .collect(Collectors.groupingBy(DriveTripSample::carId));
        List<CarSamples> result = new ArrayList<>();
        tripsByCar.forEach((carId, trips) -> {
            Car c = byId.get(carId);
            User owner = owners.get(c.getUserId());
            List<DriveTripSample> visible = trips.stream().filter(t -> isVisible(t, c, owner)).toList();
            result.add(new CarSamples(c.getUserId(), c.getEffectiveBatteryCapacityKwh(), visible));
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
