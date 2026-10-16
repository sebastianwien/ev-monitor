package com.evmonitor.application.vehiclespec;

import com.evmonitor.application.vehiclespec.AdminSpecResponses.Candidate;
import com.evmonitor.application.vehiclespec.AdminSpecResponses.CarForReassign;
import com.evmonitor.application.vehiclespec.AdminSpecResponses.ModelGroup;
import com.evmonitor.application.vehiclespec.AdminSpecResponses.ModelWithoutRow;
import com.evmonitor.application.vehiclespec.AdminSpecResponses.Overview;
import com.evmonitor.application.vehiclespec.AdminSpecResponses.SpecRow;
import com.evmonitor.application.vehiclespec.AdminSpecResponses.WorkList;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.VehicleSpecification;
import com.evmonitor.domain.VehicleSpecificationRepository;
import com.evmonitor.domain.rating.CarRatingRepository;
import com.evmonitor.infrastructure.persistence.CarEntity;
import com.evmonitor.infrastructure.persistence.JpaCarRepository;
import com.evmonitor.infrastructure.persistence.JpaEvLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Baut die Übersicht des Admin-Tabs: Zeilen je Modell mit Zählern und Arbeitslisten-Flags, alles mit wenigen Gruppen-Queries. */
@Component
@RequiredArgsConstructor
public class AdminSpecOverviewAssembler {

    private final VehicleSpecificationRepository specRepository;
    private final JpaCarRepository jpaCarRepository;
    private final JpaEvLogRepository jpaEvLogRepository;
    private final CarRatingRepository carRatingRepository;

    private record Counts(long all, long active) {
    }

    private record ModelCounts(long active, long withoutSpec) {
    }

    public Overview build() {
        Map<UUID, Counts> carsPerSpec = new HashMap<>();
        for (Object[] r : jpaCarRepository.countCarsPerSpecification()) {
            carsPerSpec.put((UUID) r[0], new Counts(((Number) r[1]).longValue(), ((Number) r[2]).longValue()));
        }
        Map<UUID, Long> ratingsPerSpec = new HashMap<>();
        for (Object[] r : carRatingRepository.countPerSpecification()) {
            ratingsPerSpec.put((UUID) r[0], ((Number) r[1]).longValue());
        }
        Map<String, ModelCounts> carsPerModel = new HashMap<>();
        for (Object[] r : jpaCarRepository.countActiveCarsPerModel()) {
            carsPerModel.put(String.valueOf(r[0]), new ModelCounts(((Number) r[1]).longValue(), ((Number) r[2]).longValue()));
        }
        Map<String, Long> logsPerModel = new HashMap<>();
        for (Object[] r : jpaCarRepository.countLogsPerModel()) {
            logsPerModel.put(String.valueOf(r[0]), ((Number) r[1]).longValue());
        }

        Map<String, List<VehicleSpecification>> byModel = specRepository.findAll().stream()
                .collect(Collectors.groupingBy(VehicleSpecification::getCarModel));

        List<ModelGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<VehicleSpecification>> e : byModel.entrySet()) {
            String model = e.getKey();
            CarBrand.CarModel enumModel = enumOf(model);
            ModelCounts mc = carsPerModel.getOrDefault(model, new ModelCounts(0, 0));
            List<SpecRow> rows = e.getValue().stream()
                    .sorted(ROW_ORDER)
                    .map(s -> row(s, carsPerSpec.getOrDefault(s.getId(), new Counts(0, 0)), ratingsPerSpec.getOrDefault(s.getId(), 0L), enumModel == null))
                    .toList();
            groups.add(new ModelGroup(e.getValue().get(0).getCarBrand(), model,
                    enumModel == null ? model : enumModel.getDisplayName(), enumModel != null,
                    logsPerModel.getOrDefault(model, 0L), mc.active(), mc.withoutSpec(), rows));
        }
        groups.sort(Comparator.comparingLong(ModelGroup::logCount).reversed().thenComparing(ModelGroup::carModel));

        List<ModelWithoutRow> withoutRow = new ArrayList<>();
        for (CarBrand.CarModel m : CarBrand.CarModel.values()) {
            if (m == CarBrand.CarModel.SONSTIGE_CUSTOM || byModel.containsKey(m.name())) continue;
            ModelCounts mc = carsPerModel.getOrDefault(m.name(), new ModelCounts(0, 0));
            withoutRow.add(new ModelWithoutRow(m.getBrand().name(), m.name(), m.getDisplayName(), mc.active(),
                    logsPerModel.getOrDefault(m.name(), 0L)));
        }
        withoutRow.sort(Comparator.comparingLong(ModelWithoutRow::activeCars).reversed().thenComparing(ModelWithoutRow::carModel));

        long carsWithoutSpec = carsPerModel.values().stream().mapToLong(ModelCounts::withoutSpec).sum();
        return new Overview(groups, withoutRow, carsWithoutSpec);
    }

    /** Einzelzeile mit eigenen Zählern, für Antworten nach Anlegen und Bearbeiten. */
    public SpecRow row(VehicleSpecification spec) {
        long all = jpaCarRepository.countByVehicleSpecificationId(spec.getId());
        long active = jpaCarRepository.findAllByVehicleSpecificationIdAndDeletedAtIsNull(spec.getId()).size();
        long ratings = carRatingRepository.countByVehicleSpecificationId(spec.getId());
        return row(spec, new Counts(all, active), ratings, enumOf(spec.getCarModel()) == null);
    }

    public List<CarForReassign> carsForReassign(CarBrand.CarModel model) {
        List<VehicleSpecification> rows = wltpRows(model.name());
        return byLogCount(jpaCarRepository.findAllByModelAndVehicleSpecificationIdIsNullAndDeletedAtIsNull(model).stream()
                .map(car -> forReassign(car, car.getCustomNetCapacityKwh(), null, rows))
                .toList());
    }

    /** Aktive Autos einer Zeile; Kandidaten sind die übrigen WLTP-Zeilen des Modells, Diff gegen das Netto dieser Zeile. */
    public List<CarForReassign> carsOfSpec(VehicleSpecification spec) {
        List<VehicleSpecification> others = wltpRows(spec.getCarModel()).stream()
                .filter(s -> !s.getId().equals(spec.getId()))
                .toList();
        return byLogCount(jpaCarRepository.findAllByVehicleSpecificationIdAndDeletedAtIsNull(spec.getId()).stream()
                .map(car -> forReassign(car, spec.getNetBatteryCapacityKwh(), spec.getId(), others))
                .toList());
    }

    // ── intern ───────────────────────────────────────────────────────────────

    private List<VehicleSpecification> wltpRows(String carModel) {
        return specRepository.findAllByCarModel(carModel).stream()
                .filter(s -> s.getRatingSource() == VehicleSpecification.RatingSource.WLTP)
                .sorted(ROW_ORDER)
                .toList();
    }

    private CarForReassign forReassign(CarEntity car, BigDecimal net, UUID currentSpecId, List<VehicleSpecification> rows) {
        List<Candidate> candidates = rows.stream()
                .map(s -> new Candidate(s.getId(), s.getVariantName(), s.getTrimLevel(), s.getNetBatteryCapacityKwh(),
                        net == null || s.getNetBatteryCapacityKwh() == null ? null : s.getNetBatteryCapacityKwh().subtract(net).abs(),
                        s.getAvailableFrom(), s.getAvailableTo()))
                .sorted(Comparator.comparing(Candidate::diffKwh, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return new CarForReassign(car.getId(), car.getModel() == null ? null : car.getModel().name(), car.getYear(), car.getTrim(),
                car.getCustomNetCapacityKwh(), currentSpecId, jpaEvLogRepository.countByCarId(car.getId()), candidates);
    }

    private static List<CarForReassign> byLogCount(List<CarForReassign> cars) {
        List<CarForReassign> result = new ArrayList<>(cars);
        result.sort(Comparator.comparingLong(CarForReassign::logCount).reversed());
        return result;
    }

    /** Neueste zuerst: available_from absteigend, ohne Datum zuletzt, dann Kapazität. */
    static final Comparator<VehicleSpecification> ROW_ORDER = Comparator
            .comparing(VehicleSpecification::getAvailableFrom, Comparator.nullsLast(Comparator.<LocalDate>reverseOrder()))
            .thenComparing(VehicleSpecification::getBatteryCapacityKwh, Comparator.reverseOrder());

    private static CarBrand.CarModel enumOf(String model) {
        try {
            return CarBrand.CarModel.valueOf(model);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static SpecRow row(VehicleSpecification s, Counts cars, long ratings, boolean withoutEnumModel) {
        EnumSet<WorkList> lists = EnumSet.noneOf(WorkList.class);
        String name = s.getVariantName() == null ? "" : s.getVariantName().trim();
        if (name.isEmpty()) lists.add(WorkList.NO_NAME);
        if (SpecRules.sanitize(s.getTrimLevel()).isEmpty()) lists.add(WorkList.NO_TRIM);
        if (s.getAvailableFrom() == null) lists.add(WorkList.NO_PERIOD);
        if (s.getSourceUrl() == null || s.getSourceUrl().isBlank()) lists.add(WorkList.NO_SOURCE);
        if (s.getNetBatteryCapacityKwh() == null) lists.add(WorkList.NO_NET);
        if (SpecRules.USER_ROW_NAME.matcher(name).matches()) lists.add(WorkList.USER_ROW);
        if (withoutEnumModel) lists.add(WorkList.ROW_WITHOUT_ENUM_MODEL);
        return new SpecRow(s.getId(), s.getCarBrand(), s.getCarModel(), s.getBatteryCapacityKwh(), s.getNetBatteryCapacityKwh(),
                s.getOfficialRangeKm(), s.getOfficialConsumptionKwhPer100km(), s.getRatingSource().name(),
                name.isEmpty() ? null : name, SpecRules.sanitize(s.getTrimLevel()).isEmpty() ? null : s.getTrimLevel(),
                s.getAvailableFrom(), s.getAvailableTo(), s.getSourceUrl(),
                s.getDcMaxPowerKw(), s.getDcAvgPower1080Kw(), cars.active(), cars.all(), ratings, List.copyOf(lists));
    }
}
