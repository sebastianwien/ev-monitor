package com.evmonitor.application.vehiclespec;

import com.evmonitor.application.vehiclespec.AdminSpecResponses.CarForReassign;
import com.evmonitor.application.vehiclespec.AdminSpecResponses.SaveResult;
import com.evmonitor.application.vehiclespec.AdminSpecResponses.SpecRow;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.VehicleSpecification;
import com.evmonitor.domain.VehicleSpecificationRepository;
import com.evmonitor.domain.exception.ConflictException;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.domain.exception.ValidationException;
import com.evmonitor.domain.rating.CarRating;
import com.evmonitor.domain.rating.CarRatingRepository;
import com.evmonitor.domain.vehiclespec.VehicleSpecificationChange;
import com.evmonitor.domain.vehiclespec.VehicleSpecificationChange.Action;
import com.evmonitor.domain.vehiclespec.VehicleSpecificationChange.Entry;
import com.evmonitor.domain.vehiclespec.VehicleSpecificationChangeRepository;
import com.evmonitor.infrastructure.persistence.JpaCarRepository;
import com.evmonitor.infrastructure.persistence.JpaEvLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Admin-Tab Fahrzeug-Specs: Zeilen anlegen, bearbeiten, löschen und Autos umhängen.
 * Jede Regel liegt hier, jeder Fehler ist eine Domain-Exception (4xx), nie ein Constraint-Verstoß.
 * Jeder Write leert die Modell-Caches, weil Rangliste und Modellseite aus den Zeilen rechnen.
 */
@Service
@RequiredArgsConstructor
public class AdminVehicleSpecificationService {

    static final String[] MODEL_CACHES = {"modelStats", "modelsWithData", "fastChargePower", "topModels", "modelsWithoutData"};

    private final VehicleSpecificationRepository specRepository;
    private final VehicleSpecificationChangeRepository changeRepository;
    private final CarRepository carRepository;
    private final JpaCarRepository jpaCarRepository;
    private final JpaEvLogRepository jpaEvLogRepository;
    private final CarRatingRepository carRatingRepository;
    private final AdminSpecOverviewAssembler overviewAssembler;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AdminSpecResponses.Overview overview() {
        return overviewAssembler.build();
    }

    @Transactional(readOnly = true)
    public List<AdminSpecResponses.ChangeEntry> changes(UUID specId) {
        return changeRepository.findAllBySpecIdOrderByChangedAtDesc(specId).stream()
                .map(c -> new AdminSpecResponses.ChangeEntry(c.getId(), c.getAdminUserId(), c.getChangedAt(),
                        c.getAction().name(), c.getField(), c.getOldValue(), c.getNewValue(), c.getWarning()))
                .toList();
    }

    @Transactional
    @CacheEvict(cacheNames = {"modelStats", "modelsWithData", "fastChargePower", "topModels", "modelsWithoutData"}, allEntries = true)
    public SaveResult create(UUID adminId, SpecCommand cmd) {
        SpecRules.requireHard(cmd, true);
        VehicleSpecification spec = apply(VehicleSpecification.builder()
                .id(UUID.randomUUID()).createdAt(LocalDateTime.now())
                .wltpType(VehicleSpecification.WltpType.COMBINED).build(), cmd);
        List<VehicleSpecification> siblings = specRepository.findAllByCarModel(spec.getCarModel());
        requireUnique(spec, siblings);
        List<String> warnings = SpecRules.warnings(spec, siblings);
        VehicleSpecification saved = specRepository.save(spec);
        log(new Entry(saved.getId(), adminId, Action.CREATE, null, null, snapshot(saved), joinOrNull(warnings)));
        return new SaveResult(overviewAssembler.row(saved), warnings);
    }

    @Transactional
    @CacheEvict(cacheNames = {"modelStats", "modelsWithData", "fastChargePower", "topModels", "modelsWithoutData"}, allEntries = true)
    public SaveResult update(UUID adminId, UUID id, SpecCommand cmd) {
        VehicleSpecification before = require(id);
        SpecRules.requireHard(cmd, false);
        VehicleSpecification after = apply(before, cmd);
        List<VehicleSpecification> siblings = specRepository.findAllByCarModel(after.getCarModel());
        requireUnique(after, siblings);
        List<String> warnings = SpecRules.warnings(after, siblings);
        VehicleSpecification saved = specRepository.save(after);
        String warning = joinOrNull(warnings);
        for (FieldDiff diff : diff(before, saved)) {
            log(new Entry(id, adminId, Action.UPDATE, diff.field(), diff.oldValue(), diff.newValue(), warning));
        }
        return new SaveResult(overviewAssembler.row(saved), warnings);
    }

    @Transactional
    @CacheEvict(cacheNames = {"modelStats", "modelsWithData", "fastChargePower", "topModels", "modelsWithoutData"}, allEntries = true)
    public void delete(UUID adminId, UUID id, String reason) {
        VehicleSpecification spec = require(id);
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("Begründung ist Pflicht (Quelle oder Grund für das Löschen).");
        }
        long cars = jpaCarRepository.countByVehicleSpecificationId(id);
        long ratings = carRatingRepository.countByVehicleSpecificationId(id);
        if (cars > 0 || ratings > 0) {
            throw new ConflictException("SPEC_IN_USE", "Zeile hängt an " + cars + " Auto(s) (auch gelöschte im Papierkorb) und "
                    + ratings + " Bewertung(en). Erst Autos umhängen.");
        }
        specRepository.deleteById(id);
        log(new Entry(id, adminId, Action.DELETE, null, snapshot(spec), reason.trim(), null));
    }

    @Transactional
    @CacheEvict(cacheNames = {"modelStats", "modelsWithData", "fastChargePower", "topModels", "modelsWithoutData"}, allEntries = true)
    public void reassignCar(UUID adminId, UUID carId, UUID specId) {
        VehicleSpecification spec = require(specId);
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new NotFoundException("Auto nicht gefunden."));
        if (car.getModel() == null || !car.getModel().name().equals(spec.getCarModel())) {
            throw new ValidationException("Auto (" + car.getModel() + ") und Zeile (" + spec.getCarModel()
                    + ") sind nicht dasselbe Modell.");
        }
        BigDecimal oldNet = car.getNominalNetCapacityKwh();
        carRepository.save(car.toBuilder()
                .vehicleSpecificationId(specId)
                .customNetCapacityKwh(null)
                .updatedAt(LocalDateTime.now())
                .build());
        for (CarRating rating : carRatingRepository.findAllByCarId(carId)) {
            rating.relinkSpecification(specId);
        }
        log(new Entry(specId, adminId, Action.REASSIGN_CAR, "car:" + carId,
                plain(oldNet), plain(spec.getNetBatteryCapacityKwh()), null));
    }

    @Transactional(readOnly = true)
    public List<CarForReassign> carsForReassign(CarBrand.CarModel model) {
        return overviewAssembler.carsForReassign(model);
    }

    @Transactional(readOnly = true)
    public List<CarForReassign> carsOfSpec(UUID specId) {
        return overviewAssembler.carsOfSpec(require(specId));
    }

    // ── intern ───────────────────────────────────────────────────────────────

    private VehicleSpecification require(UUID id) {
        return specRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Spec-Zeile nicht gefunden."));
    }

    private VehicleSpecification apply(VehicleSpecification base, SpecCommand cmd) {
        return base.toBuilder()
                .carBrand(cmd.model().getBrand().name())
                .carModel(cmd.model().name())
                .batteryCapacityKwh(cmd.batteryCapacityKwh())
                .netBatteryCapacityKwh(cmd.netBatteryCapacityKwh())
                .officialRangeKm(cmd.officialRangeKm())
                .officialConsumptionKwhPer100km(cmd.officialConsumptionKwhPer100km())
                .ratingSource(cmd.ratingSource() == null ? VehicleSpecification.RatingSource.WLTP : cmd.ratingSource())
                .variantName(SpecRules.sanitize(cmd.variantName()))
                .trimLevel(SpecRules.sanitize(cmd.trimLevel()))
                .availableFrom(cmd.availableFrom())
                .availableTo(cmd.availableTo())
                .sourceUrl(cmd.sourceUrl().trim())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private void requireUnique(VehicleSpecification spec, List<VehicleSpecification> siblings) {
        siblings.stream()
                .filter(o -> !o.getId().equals(spec.getId()))
                .filter(o -> SpecRules.sameUniqueKey(o, spec))
                .findFirst()
                .ifPresent(o -> {
                    throw new ConflictException("SPEC_DUPLICATE", "Diese Zeile gibt es schon: " + o.getBatteryCapacityKwh()
                            + " kWh \"" + o.getVariantName() + "\" (" + o.getRatingSource() + "). Name ändern oder die vorhandene bearbeiten.");
                });
    }

    private void log(Entry entry) {
        changeRepository.save(VehicleSpecificationChange.of(entry));
    }

    private String snapshot(VehicleSpecification spec) {
        try {
            return objectMapper.writeValueAsString(overviewAssembler.row(spec));
        } catch (JsonProcessingException e) {
            return spec.toString();
        }
    }

    record FieldDiff(String field, String oldValue, String newValue) {
    }

    private static List<FieldDiff> diff(VehicleSpecification a, VehicleSpecification b) {
        List<FieldDiff> diffs = new ArrayList<>();
        add(diffs, "carModel", a.getCarModel(), b.getCarModel());
        add(diffs, "batteryCapacityKwh", plain(a.getBatteryCapacityKwh()), plain(b.getBatteryCapacityKwh()));
        add(diffs, "netBatteryCapacityKwh", plain(a.getNetBatteryCapacityKwh()), plain(b.getNetBatteryCapacityKwh()));
        add(diffs, "officialRangeKm", plain(a.getOfficialRangeKm()), plain(b.getOfficialRangeKm()));
        add(diffs, "officialConsumptionKwhPer100km", plain(a.getOfficialConsumptionKwhPer100km()), plain(b.getOfficialConsumptionKwhPer100km()));
        add(diffs, "ratingSource", a.getRatingSource().name(), b.getRatingSource().name());
        add(diffs, "variantName", a.getVariantName(), b.getVariantName());
        add(diffs, "trimLevel", SpecRules.sanitize(a.getTrimLevel()), SpecRules.sanitize(b.getTrimLevel()));
        add(diffs, "availableFrom", str(a.getAvailableFrom()), str(b.getAvailableFrom()));
        add(diffs, "availableTo", str(a.getAvailableTo()), str(b.getAvailableTo()));
        add(diffs, "sourceUrl", a.getSourceUrl(), b.getSourceUrl());
        return diffs;
    }

    private static void add(List<FieldDiff> diffs, String field, String oldValue, String newValue) {
        if (!Objects.equals(oldValue, newValue)) diffs.add(new FieldDiff(field, oldValue, newValue));
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static String joinOrNull(List<String> warnings) {
        return warnings.isEmpty() ? null : String.join(" | ", warnings);
    }
}
