package com.evmonitor.application.community;

import com.evmonitor.application.CommunityConsumptionResult;
import com.evmonitor.application.ConsumptionResult;
import com.evmonitor.application.PlausibilityProperties;
import com.evmonitor.application.RouteTypeConsumptionResult;
import com.evmonitor.application.SeasonalConsumptionResult;
import com.evmonitor.application.consumption.ConsumptionCalculationService;
import com.evmonitor.application.consumption.ConsumptionMath;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.RouteType;
import com.evmonitor.domain.RouteTypeSource;
import com.evmonitor.infrastructure.persistence.JpaVehicleSpecificationRepository;
import com.evmonitor.infrastructure.persistence.VehicleSpecificationEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Verbrauch aus Ladungen über viele Autos (Modell, Variante, Peers): Community-Schnitt, Saisons,
 * Streckentypen. Dieselben Regeln je Ladung wie die Statistik eines eigenen Autos
 * ({@code EvLogStatisticsService}), nur ohne Nutzerbezug. Modellseiten, Rangliste, Rechner und die
 * Reichweiten-Schätzung der Statistik hängen hier, nicht aneinander.
 */
@Service
@RequiredArgsConstructor
public class CommunityConsumptionService {

    /** Fahrer-Spanne (25./75. Perzentil) erst ab so vielen plausiblen Ladungen je Auto. */
    private static final int MIN_TRIPS_FOR_CAR_RANGE = 5;

    private final EvLogRepository evLogRepository;
    private final ConsumptionCalculationService calculationService;
    private final PlausibilityProperties plausibility;
    private final CarRepository carRepository;
    private final JpaVehicleSpecificationRepository vehicleSpecificationRepository;

    /** Ladungen aller Fahrer eines Modells: Schnitt, Saisons und die Zahl der Autos dahinter. */
    public record ModelCommunity(CommunityConsumptionResult overall, SeasonalConsumptionResult seasonal) {
        public static final ModelCommunity NONE = new ModelCommunity(CommunityConsumptionResult.EMPTY,
                new SeasonalConsumptionResult(null, null, null, 0, 0, 0, 0));
        public int carCount() { return overall.carCount(); }
    }

    /**
     * Die Modellwerte, die Modellseite, Rangliste und die Reichweiten-Schätzung der Statistik teilen,
     * je Modell 4 h gecacht: ein Dashboard-Aufruf baut damit keine Modellseite mehr.
     */
    @Cacheable(value = "communityModel", key = "#model.name() + '-' + #isSeedUser")
    public ModelCommunity forModel(CarBrand.CarModel model, boolean isSeedUser) {
        List<Car> cars = carRepository.findAllByModel(model);
        if (cars.isEmpty()) return ModelCommunity.NONE;
        return new ModelCommunity(calculateCommunityAvgConsumption(cars, isSeedUser), calculateSeasonalConsumption(cars, isSeedUser));
    }

    /**
     * Wie weit die Flotte über WLTP liegt, als Median über alle Modelle mit WLTP-Spec und genug Autos
     * ({@link WltpFleetFactors}); 4 h gecacht. Rechnet die Modelle direkt (ein Selbstaufruf liefe am
     * forModel-Cache vorbei), das ist einmal je Cache-Lauf hinnehmbar.
     */
    @Cacheable(value = "fleetFactors", key = "#isSeedUser")
    public WltpFleetFactors fleetFactors(boolean isSeedUser) {
        Map<String, List<VehicleSpecificationEntity>> specsByModel = vehicleSpecificationRepository.findByRatingSource("WLTP").stream()
                .collect(Collectors.groupingBy(VehicleSpecificationEntity::getCarModel));
        List<WltpFleetFactors.Sample> samples = new ArrayList<>();
        specsByModel.forEach((modelName, specs) -> {
            CarBrand.CarModel model;
            try {
                model = CarBrand.CarModel.valueOf(modelName);
            } catch (IllegalArgumentException e) {
                return;
            }
            if (model.getBrand() == CarBrand.SONSTIGE) return;
            BigDecimal wltp = averageWltp(specs);
            if (wltp == null) return;
            ModelCommunity c = forModel(model, isSeedUser);
            if (c.carCount() < WltpFleetFactors.MIN_CARS_PER_MODEL) return;
            samples.add(new WltpFleetFactors.Sample(c.carCount(), wltp, c.overall().value(),
                    c.seasonal().summerConsumptionKwhPer100km(), c.seasonal().winterConsumptionKwhPer100km()));
        });
        return WltpFleetFactors.from(samples);
    }

    /** Mittel der WLTP-Verbräuche aller Varianten eines Modells, eine Stelle. */
    public static BigDecimal averageWltp(List<VehicleSpecificationEntity> specs) {
        List<BigDecimal> values = specs.stream().map(VehicleSpecificationEntity::getOfficialConsumptionKwhPer100km)
                .filter(java.util.Objects::nonNull).toList();
        if (values.isEmpty()) return null;
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 1, RoundingMode.HALF_UP);
    }

    /** Eine plausible Ladung mit ihrem Verbrauch, aus SoC oder als kWh-je-km-Schätzung. */
    public record PlausibleEntry(EvLog log, BigDecimal consumptionKwhPer100km, int distanceKm, boolean estimated) {}

    /**
     * Returns all plausible consumption entries for a car: SoC-based first, fallback if no SoC data.
     * Encapsulates the two-path logic shared by community avg, seasonal calculations and the peer benchmark.
     */
    public List<PlausibleEntry> getPlausibleEntriesForCar(Car car, List<EvLog> allLogs, List<EvLog> statsLogs) {
        Map<UUID, ConsumptionResult> perLog = calculationService.calculateConsumptionPerLog(
                allLogs, calculationService.buildCapacityLookup(car), calculationService.lookupWltp(car));

        List<PlausibleEntry> entries = new ArrayList<>();

        // Pass 1: SoC-based consumption (plausible only)
        for (EvLog log : statsLogs) {
            ConsumptionResult cr = perLog.get(log.getId());
            if (cr == null || !cr.plausible()) continue;
            entries.add(new PlausibleEntry(log, cr.value(), cr.distanceKm(), false));
        }

        // Pass 2: Fallback kWh/distance for logs without SoC result (hybrid approach)
        // This allows using old Sprit-Monitor imports without SoC data alongside newer logs with SoC.
        // Marked as estimated (less accurate, but better than nothing).
        Map<UUID, Integer> distanceByLogId = calculationService.computeDistanceByLogId(allLogs);
        for (EvLog log : statsLogs) {
            // Skip if already computed via SoC (plausible)
            if (perLog.containsKey(log.getId()) && perLog.get(log.getId()).plausible()) continue;

            Integer dist = distanceByLogId.get(log.getId());
            if (dist == null || dist < plausibility.getMinTripDistanceKm()) continue;
            if (!log.hasEnergyData()) continue;

            double c = calculationService.effectiveKwhForConsumption(log).doubleValue() / dist * 100;
            if (c < plausibility.getAbsoluteMinKwhPer100km() || c > plausibility.getAbsoluteMaxKwhPer100km()) continue;

            entries.add(new PlausibleEntry(log, BigDecimal.valueOf(c).setScale(2, RoundingMode.HALF_UP), dist, true));
        }
        return entries;
    }

    /**
     * Calculates the community average consumption (kWh/100km) for a list of cars.
     *
     * Uses the same logic as per-user statistics (SoC-based → fallback), applied per car.
     * Results are distance-weighted across all cars to give a fair community average.
     *
     * isSeedUser=true: includes all logs (for demo mode).
     * isSeedUser=false: only logs with includeInStatistics=true.
     *
     * Trips with implausible consumption (outside absoluteMin/Max) are excluded
     * in the fallback path via per-trip filtering with PlausibilityProperties.
     *
     * @return distance-weighted avg kWh/100km, or null if no valid data
     */
    public CommunityConsumptionResult calculateCommunityAvgConsumption(List<Car> cars, boolean isSeedUser) {
        return calculateCommunityAvgConsumption(cars, isSeedUser, null, null);
    }

    /**
     * Variante mit optionalem Zeitraum: nur Logs innerhalb [startDate, endDate] zählen als Trips,
     * die Vollhistorie bleibt Kontext für SoC- und Distanz-Differenzen (wie bei der Nutzer-Statistik).
     */
    public CommunityConsumptionResult calculateCommunityAvgConsumption(
            List<Car> cars, boolean isSeedUser, java.time.LocalDate startDate, java.time.LocalDate endDate) {
        if (cars.isEmpty()) return CommunityConsumptionResult.EMPTY;

        List<UUID> carIds = cars.stream().map(Car::getId).toList();
        Map<UUID, List<EvLog>> logsByCarId = evLogRepository.findAllByCarIds(carIds).stream()
                .sorted(Comparator.comparing(EvLog::getLoggedAt))
                .collect(Collectors.groupingBy(EvLog::getCarId));

        BigDecimal totalWeighted = BigDecimal.ZERO;
        int totalDistance = 0;
        int tripCount = 0;
        int estimatedTripCount = 0;
        List<BigDecimal> perCarAverages = new ArrayList<>();
        List<List<PlausibleEntry>> allCarEntries = new ArrayList<>();
        int carCount = 0;

        for (Car car : cars) {
            List<EvLog> allLogs = logsByCarId.getOrDefault(car.getId(), List.of());
            List<EvLog> statsLogs = allLogs.stream()
                    .filter(l -> isSeedUser || l.isIncludeInStatistics())
                    .filter(l -> l.isLoggedWithin(startDate, endDate))
                    .toList();
            if (statsLogs.isEmpty()) continue;

            List<PlausibleEntry> entries = getPlausibleEntriesForCar(car, allLogs, statsLogs);
            BigDecimal carWeighted = BigDecimal.ZERO;
            int carDistance = 0;
            for (PlausibleEntry e : entries) {
                BigDecimal weighted = e.consumptionKwhPer100km().multiply(BigDecimal.valueOf(e.distanceKm()));
                carWeighted = carWeighted.add(weighted);
                carDistance += e.distanceKm();
                if (e.estimated()) estimatedTripCount++;
            }
            tripCount += entries.size();
            if (!entries.isEmpty()) carCount++;
            totalWeighted = totalWeighted.add(carWeighted);
            totalDistance += carDistance;
            BigDecimal carAvg = ConsumptionMath.weightedAverage(carWeighted, carDistance);
            if (carAvg != null && entries.size() >= MIN_TRIPS_FOR_CAR_RANGE) perCarAverages.add(carAvg);
            allCarEntries.add(entries);
        }

        Collections.sort(perCarAverages);
        BigDecimal minValue;
        BigDecimal maxValue;
        CommunityConsumptionResult.RangeSource rangeSource;
        if (perCarAverages.size() >= 2) {
            minValue = interpolatedPercentile(perCarAverages, 0.25);
            maxValue = interpolatedPercentile(perCarAverages, 0.75);
            rangeSource = CommunityConsumptionResult.RangeSource.PER_DRIVER;
        } else {
            List<BigDecimal> allTripConsumptions = allCarEntries.stream()
                    .flatMap(Collection::stream)
                    .map(PlausibleEntry::consumptionKwhPer100km)
                    .sorted()
                    .collect(Collectors.toCollection(ArrayList::new));
            if (allTripConsumptions.size() >= MIN_TRIPS_FOR_CAR_RANGE) {
                minValue = interpolatedPercentile(allTripConsumptions, 0.25);
                maxValue = interpolatedPercentile(allTripConsumptions, 0.75);
                rangeSource = CommunityConsumptionResult.RangeSource.PER_TRIP;
            } else {
                minValue = null;
                maxValue = null;
                rangeSource = null;
            }
        }

        return new CommunityConsumptionResult(ConsumptionMath.weightedAverage(totalWeighted, totalDistance), minValue, maxValue, rangeSource, tripCount, estimatedTripCount, carCount);
    }

    /**
     * Calculates seasonal (summer/winter) community consumption for a list of cars.
     * Summer: April-September (months 4-9), Winter: October-March (months 1-3, 10-12).
     *
     * Uses the same SoC-based logic as calculateCommunityAvgConsumption(), bucketed by season.
     * Fallback (kWh/distance) is applied per-car when no SoC data is available.
     *
     * @return SeasonalConsumptionResult with nullable consumptions if no data for a season
     */
    public SeasonalConsumptionResult calculateSeasonalConsumption(List<Car> cars, boolean isSeedUser) {
        if (cars.isEmpty()) return new SeasonalConsumptionResult(null, null, null, 0, 0, 0, 0);
        List<UUID> carIds = cars.stream().map(Car::getId).toList();
        return calculateSeasonalConsumption(cars, evLogRepository.findAllByCarIds(carIds), isSeedUser);
    }

    /**
     * Wie {@link #calculateSeasonalConsumption(List, boolean)}, mit schon geladenen Ladungen: die Statistik
     * eines Autos hat seine Historie bereits in der Hand und lädt sie nicht ein zweites Mal.
     */
    public SeasonalConsumptionResult calculateSeasonalConsumption(List<Car> cars, List<EvLog> logs, boolean isSeedUser) {
        if (cars.isEmpty()) return new SeasonalConsumptionResult(null, null, null, 0, 0, 0, 0);

        Map<UUID, List<EvLog>> logsByCarId = logs.stream()
                .sorted(Comparator.comparing(EvLog::getLoggedAt))
                .collect(Collectors.groupingBy(EvLog::getCarId));

        BigDecimal summerWeighted = BigDecimal.ZERO;
        BigDecimal winterWeighted = BigDecimal.ZERO;
        BigDecimal totalWeighted = BigDecimal.ZERO;
        int summerKm = 0, winterKm = 0, totalKm = 0, summerLogCount = 0, winterLogCount = 0;

        for (Car car : cars) {
            List<EvLog> allLogs = logsByCarId.getOrDefault(car.getId(), List.of());
            List<EvLog> statsLogs = allLogs.stream()
                    .filter(l -> isSeedUser || l.isIncludeInStatistics())
                    .toList();
            if (statsLogs.isEmpty()) continue;

            for (PlausibleEntry e : getPlausibleEntriesForCar(car, allLogs, statsLogs)) {
                BigDecimal weighted = e.consumptionKwhPer100km().multiply(BigDecimal.valueOf(e.distanceKm()));
                int month = e.log().getLoggedAt().getMonthValue();
                totalWeighted = totalWeighted.add(weighted);
                totalKm += e.distanceKm();
                if (month >= 5 && month <= 8) {
                    summerWeighted = summerWeighted.add(weighted);
                    summerKm += e.distanceKm();
                    summerLogCount++;
                } else if (month == 11 || month == 12 || month == 1 || month == 2) {
                    winterWeighted = winterWeighted.add(weighted);
                    winterKm += e.distanceKm();
                    winterLogCount++;
                }
            }
        }

        return new SeasonalConsumptionResult(
                ConsumptionMath.weightedAverage(summerWeighted, summerKm),
                ConsumptionMath.weightedAverage(winterWeighted, winterKm),
                ConsumptionMath.weightedAverage(totalWeighted, totalKm),
                summerKm, winterKm, summerLogCount, winterLogCount);
    }

    /**
     * Community consumption per route type for a list of cars. Same plausible entries
     * (SoC first, kWh/distance fallback) as calculateSeasonalConsumption(), bucketed by the
     * route type of the log that closes the trip. Logs without route type count nowhere.
     */
    public RouteTypeConsumptionResult calculateRouteTypeConsumption(List<Car> cars, boolean isSeedUser) {
        if (cars.isEmpty()) return RouteTypeConsumptionResult.empty();

        List<UUID> carIds = cars.stream().map(Car::getId).toList();
        Map<UUID, List<EvLog>> logsByCarId = evLogRepository.findAllByCarIds(carIds).stream()
                .sorted(Comparator.comparing(EvLog::getLoggedAt))
                .collect(Collectors.groupingBy(EvLog::getCarId));

        Map<RouteType, BigDecimal> weighted = new java.util.EnumMap<>(RouteType.class);
        Map<RouteType, Integer> km = new java.util.EnumMap<>(RouteType.class);
        Map<RouteType, Integer> count = new java.util.EnumMap<>(RouteType.class);
        java.util.Set<UUID> highwayCars = new java.util.HashSet<>();

        for (Car car : cars) {
            List<EvLog> allLogs = logsByCarId.getOrDefault(car.getId(), List.of());
            List<EvLog> statsLogs = allLogs.stream()
                    .filter(l -> isSeedUser || l.isIncludeInStatistics())
                    .toList();
            if (statsLogs.isEmpty()) continue;

            for (PlausibleEntry e : getPlausibleEntriesForCar(car, allLogs, statsLogs)) {
                RouteType type = e.log().getRouteType();
                if (type == null) continue;
                RouteTypeSource source = e.log().getRouteTypeSource();
                if (source != null && !source.countsInStatistics()) continue; // Faustregel verzerrt den Autobahnwert
                weighted.merge(type, e.consumptionKwhPer100km().multiply(BigDecimal.valueOf(e.distanceKm())), BigDecimal::add);
                km.merge(type, e.distanceKm(), Integer::sum);
                count.merge(type, 1, Integer::sum);
                if (type == RouteType.HIGHWAY) highwayCars.add(car.getId());
            }
        }

        return new RouteTypeConsumptionResult(
                ConsumptionMath.weightedAverage(weighted.getOrDefault(RouteType.CITY, BigDecimal.ZERO), km.getOrDefault(RouteType.CITY, 0)),
                ConsumptionMath.weightedAverage(weighted.getOrDefault(RouteType.COMBINED, BigDecimal.ZERO), km.getOrDefault(RouteType.COMBINED, 0)),
                ConsumptionMath.weightedAverage(weighted.getOrDefault(RouteType.HIGHWAY, BigDecimal.ZERO), km.getOrDefault(RouteType.HIGHWAY, 0)),
                count.getOrDefault(RouteType.CITY, 0),
                count.getOrDefault(RouteType.COMBINED, 0),
                count.getOrDefault(RouteType.HIGHWAY, 0),
                highwayCars.size());
    }


    private static BigDecimal interpolatedPercentile(List<BigDecimal> sorted, double p) {
        int n = sorted.size();
        if (n == 1) return sorted.get(0).setScale(2, RoundingMode.HALF_UP);
        double pos = p * (n - 1);
        int lower = (int) Math.floor(pos);
        int upper = (int) Math.ceil(pos);
        if (lower == upper) return sorted.get(lower).setScale(2, RoundingMode.HALF_UP);
        double fraction = pos - lower;
        BigDecimal lv = sorted.get(lower);
        BigDecimal uv = sorted.get(upper);
        return lv.add(uv.subtract(lv).multiply(BigDecimal.valueOf(fraction)))
                 .setScale(2, RoundingMode.HALF_UP);
    }
}
