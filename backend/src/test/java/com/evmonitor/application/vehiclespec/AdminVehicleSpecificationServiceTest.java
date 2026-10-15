package com.evmonitor.application.vehiclespec;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.User;
import com.evmonitor.domain.VehicleSpecification;
import com.evmonitor.domain.exception.ConflictException;
import com.evmonitor.domain.exception.ValidationException;
import com.evmonitor.domain.rating.CarRating;
import com.evmonitor.domain.rating.CarRatingRepository;
import com.evmonitor.domain.rating.RatingAnswers;
import com.evmonitor.domain.rating.RatingSnapshot;
import com.evmonitor.domain.rating.RebuyAnswer;
import com.evmonitor.domain.rating.TotalCostVerdict;
import com.evmonitor.domain.vehiclespec.VehicleSpecificationChange;
import com.evmonitor.domain.vehiclespec.VehicleSpecificationChangeRepository;
import com.evmonitor.testutil.AbstractServiceTest;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fachregeln des Admin-Tabs: jede Regel ein Test, Fehler als 4xx-Domain-Exception, nie als Constraint-Verstoß. */
@Transactional
class AdminVehicleSpecificationServiceTest extends AbstractServiceTest {

    private static final String SOURCE = "https://ev-database.org/car/1991/Tesla-Model-3";

    @Autowired private AdminVehicleSpecificationService service;
    @Autowired private VehicleSpecificationChangeRepository changeRepository;
    @Autowired private CarRatingRepository carRatingRepository;

    private UUID adminId;

    @BeforeEach
    void setUp() {
        adminId = createAndSaveUser("admin-" + UUID.randomUUID() + "@test.de").getId();
    }

    private SpecCommand validCommand() {
        return new SpecCommand(CarBrand.CarModel.MODEL_3, new BigDecimal("79.0"), new BigDecimal("75.0"),
                new BigDecimal("629"), new BigDecimal("14.0"), "Long Range AWD", "Long Range",
                LocalDate.of(2024, 1, 1), null, SOURCE, VehicleSpecification.RatingSource.WLTP);
    }

    // ── Anlegen ──────────────────────────────────────────────────────────────

    @Test
    void create_validRow_persistsAndLogsCreate() {
        AdminSpecResponses.SaveResult result = service.create(adminId, validCommand());

        VehicleSpecification saved = vehicleSpecificationRepository.findById(result.row().id()).orElseThrow();
        assertThat(saved.getCarBrand()).isEqualTo("TESLA");
        assertThat(saved.getCarModel()).isEqualTo("MODEL_3");
        assertThat(saved.getTrimLevel()).isEqualTo("Long Range");
        assertThat(saved.getSourceUrl()).isEqualTo(SOURCE);
        assertThat(saved.getWltpType()).isEqualTo(VehicleSpecification.WltpType.COMBINED);
        assertThat(result.warnings()).isEmpty();

        List<VehicleSpecificationChange> changes = changeRepository.findAllBySpecIdOrderByChangedAtDesc(saved.getId());
        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).getAction()).isEqualTo(VehicleSpecificationChange.Action.CREATE);
        assertThat(changes.get(0).getAdminUserId()).isEqualTo(adminId);
    }

    @Test
    void create_netAboveGross_rejected() {
        SpecCommand cmd = validCommand().toBuilder().netBatteryCapacityKwh(new BigDecimal("80.0")).build();
        assertThatThrownBy(() -> service.create(adminId, cmd))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Netto");
    }

    @Test
    void create_withoutTrim_rejected() {
        SpecCommand cmd = validCommand().toBuilder().trimLevel("  ").build();
        assertThatThrownBy(() -> service.create(adminId, cmd))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Trim");
    }

    @Test
    void create_withoutAvailableFrom_rejected() {
        SpecCommand cmd = validCommand().toBuilder().availableFrom(null).build();
        assertThatThrownBy(() -> service.create(adminId, cmd))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Verfügbar ab");
    }

    @Test
    void create_httpSource_rejected() {
        SpecCommand cmd = validCommand().toBuilder().sourceUrl("http://ev-database.org/x").build();
        assertThatThrownBy(() -> service.create(adminId, cmd))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("https");
    }

    @Test
    void create_periodEndsBeforeStart_rejected() {
        SpecCommand cmd = validCommand().toBuilder()
                .availableFrom(LocalDate.of(2024, 1, 1)).availableTo(LocalDate.of(2023, 1, 1)).build();
        assertThatThrownBy(() -> service.create(adminId, cmd))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Verfügbar bis");
    }

    @Test
    void create_sonstige_rejected() {
        SpecCommand cmd = validCommand().toBuilder().model(CarBrand.CarModel.SONSTIGE_CUSTOM).build();
        assertThatThrownBy(() -> service.create(adminId, cmd))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void create_duplicateUniqueKey_conflictInsteadOf500() {
        service.create(adminId, validCommand());
        assertThatThrownBy(() -> service.create(adminId, validCommand()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("gibt es schon");
    }

    @Test
    void create_stripsHtmlFromNameAndTrim() {
        SpecCommand cmd = validCommand().toBuilder()
                .variantName("<b>Long</b> Range AWD").trimLevel("Long <script>x</script>Range").build();

        AdminSpecResponses.SaveResult result = service.create(adminId, cmd);

        assertThat(result.row().variantName()).isEqualTo("Long Range AWD");
        assertThat(result.row().trimLevel()).isEqualTo("Long xRange");
    }

    // ── Warnungen: speichern erlaubt, im Protokoll vermerkt ──────────────────

    @Test
    void create_lowRatio_savedWithWarning() {
        // 500 km × 14 / 100 = 70 kWh gegen 75 kWh Netto: Verhältnis 0,93, sieht nach Realwert aus
        SpecCommand cmd = validCommand().toBuilder().officialRangeKm(new BigDecimal("500")).build();

        AdminSpecResponses.SaveResult result = service.create(adminId, cmd);

        assertThat(result.warnings()).anySatisfy(w -> assertThat(w).contains("1,10"));
        List<VehicleSpecificationChange> changes = changeRepository.findAllBySpecIdOrderByChangedAtDesc(result.row().id());
        assertThat(changes.get(0).getWarning()).contains("1,10");
    }

    @Test
    void create_userStyleName_savedWithWarning() {
        SpecCommand cmd = validCommand().toBuilder().variantName("79/75 kWh").build();
        AdminSpecResponses.SaveResult result = service.create(adminId, cmd);
        assertThat(result.warnings()).anySatisfy(w -> assertThat(w).contains("Nutzer"));
    }

    @Test
    void create_overlappingPeriodSameTrim_savedWithWarning() {
        service.create(adminId, validCommand().toBuilder()
                .variantName("Long Range AWD (2021-2023)").availableFrom(LocalDate.of(2021, 1, 1))
                .availableTo(LocalDate.of(2023, 12, 31)).batteryCapacityKwh(new BigDecimal("82.0")).build());

        AdminSpecResponses.SaveResult result = service.create(adminId, validCommand().toBuilder()
                .availableFrom(LocalDate.of(2023, 6, 1)).build());

        assertThat(result.warnings()).anySatisfy(w -> assertThat(w).contains("überlappt"));
    }

    // ── Bearbeiten ───────────────────────────────────────────────────────────

    @Test
    void update_changedFields_loggedPerField() {
        UUID id = service.create(adminId, validCommand()).row().id();

        service.update(adminId, id, validCommand().toBuilder()
                .officialRangeKm(new BigDecimal("614")).trimLevel("Long Range AWD").build());

        List<VehicleSpecificationChange> updates = changeRepository.findAllBySpecIdOrderByChangedAtDesc(id).stream()
                .filter(c -> c.getAction() == VehicleSpecificationChange.Action.UPDATE).toList();
        assertThat(updates).extracting(VehicleSpecificationChange::getField)
                .containsExactlyInAnyOrder("officialRangeKm", "trimLevel");
        assertThat(updates).filteredOn(c -> c.getField().equals("officialRangeKm"))
                .first().satisfies(c -> {
                    assertThat(c.getOldValue()).isEqualTo("629");
                    assertThat(c.getNewValue()).isEqualTo("614");
                });
        VehicleSpecification saved = vehicleSpecificationRepository.findById(id).orElseThrow();
        assertThat(saved.getTrimLevel()).isEqualTo("Long Range AWD");
    }

    @Test
    void update_toExistingUniqueKey_conflict() {
        UUID id = service.create(adminId, validCommand()).row().id();
        service.create(adminId, validCommand().toBuilder().variantName("Performance").trimLevel("Performance").build());

        assertThatThrownBy(() -> service.update(adminId, id, validCommand().toBuilder().variantName("Performance").build()))
                .isInstanceOf(ConflictException.class);
    }

    // ── Löschen ──────────────────────────────────────────────────────────────

    @Test
    void delete_withoutCars_removesAndLogsSnapshot() {
        UUID id = service.create(adminId, validCommand()).row().id();

        service.delete(adminId, id, "Doppelt zu Long Range AWD 2023");

        assertThat(vehicleSpecificationRepository.findById(id)).isEmpty();
        List<VehicleSpecificationChange> changes = changeRepository.findAllBySpecIdOrderByChangedAtDesc(id);
        assertThat(changes.get(0).getAction()).isEqualTo(VehicleSpecificationChange.Action.DELETE);
        assertThat(changes.get(0).getOldValue()).contains("Long Range AWD");
        assertThat(changes.get(0).getNewValue()).isEqualTo("Doppelt zu Long Range AWD 2023");
    }

    @Test
    void delete_withoutReason_rejected() {
        UUID id = service.create(adminId, validCommand()).row().id();
        assertThatThrownBy(() -> service.delete(adminId, id, " "))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void delete_withLinkedCar_conflictEvenIfCarSoftDeleted() {
        UUID id = service.create(adminId, validCommand()).row().id();
        User owner = createAndSaveUser("owner-" + UUID.randomUUID() + "@test.de");
        Car car = carRepository.save(TestDataBuilder.createTestCar(owner.getId(), CarBrand.CarModel.MODEL_3, null)
                .toBuilder().vehicleSpecificationId(id).build());
        carRepository.save(car.softDelete());

        assertThatThrownBy(() -> service.delete(adminId, id, "Test"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("1 Auto");
        assertThat(vehicleSpecificationRepository.findById(id)).isPresent();
    }

    // ── Umhängen ─────────────────────────────────────────────────────────────

    @Test
    void reassignCar_setsSpecClearsCustomMovesRatingAndLogsDiff() {
        UUID specId = service.create(adminId, validCommand()).row().id();
        User owner = createAndSaveUser("owner-" + UUID.randomUUID() + "@test.de");
        Car car = carRepository.save(TestDataBuilder.createTestCar(owner.getId(), CarBrand.CarModel.MODEL_3, new BigDecimal("74.0")));
        carRatingRepository.save(CarRating.create(car.getId(), owner.getId(),
                new RatingSnapshot(CarBrand.CarModel.MODEL_3, null, 0, 5, false, false),
                new RatingAnswers(RebuyAnswer.YES, List.of(), 4, null, 4, 4, null, TotalCostVerdict.EXPECTED, List.of(), List.of())));

        service.reassignCar(adminId, car.getId(), specId);

        Car reloaded = carRepository.findById(car.getId()).orElseThrow();
        assertThat(reloaded.getVehicleSpecificationId()).isEqualTo(specId);
        assertThat(reloaded.getCustomNetCapacityKwh()).isNull();
        assertThat(reloaded.getNominalNetCapacityKwh()).isEqualByComparingTo("75.0");
        assertThat(carRatingRepository.findAllByCarId(car.getId()))
                .allSatisfy(r -> assertThat(r.getVehicleSpecificationId()).isEqualTo(specId));

        VehicleSpecificationChange change = changeRepository.findAllBySpecIdOrderByChangedAtDesc(specId).get(0);
        assertThat(change.getAction()).isEqualTo(VehicleSpecificationChange.Action.REASSIGN_CAR);
        assertThat(change.getField()).isEqualTo("car:" + car.getId());
        assertThat(change.getOldValue()).isEqualTo("74");
        assertThat(change.getNewValue()).isEqualTo("75");
    }

    @Test
    void reassignCar_modelMismatch_rejected() {
        UUID specId = service.create(adminId, validCommand()).row().id();
        User owner = createAndSaveUser("owner-" + UUID.randomUUID() + "@test.de");
        Car car = carRepository.save(TestDataBuilder.createTestCar(owner.getId(), CarBrand.CarModel.MODEL_Y, new BigDecimal("74.0")));

        assertThatThrownBy(() -> service.reassignCar(adminId, car.getId(), specId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Modell");
    }

    // ── Kandidaten zum Umhängen ──────────────────────────────────────────────

    @Test
    void carsForReassign_listsCarsWithoutSpecWithDiffPerCandidate() {
        UUID specId = service.create(adminId, validCommand()).row().id();
        User owner = createAndSaveUser("owner-" + UUID.randomUUID() + "@test.de");
        Car car = carRepository.save(TestDataBuilder.createTestCar(owner.getId(), CarBrand.CarModel.MODEL_3, new BigDecimal("74.0")));
        evLogRepository.save(TestDataBuilder.createTestEvLog(car.getId(), new BigDecimal("30"), new BigDecimal("10")));

        List<AdminSpecResponses.CarForReassign> cars = service.carsForReassign(CarBrand.CarModel.MODEL_3);

        assertThat(cars).extracting(AdminSpecResponses.CarForReassign::carId).contains(car.getId());
        AdminSpecResponses.CarForReassign entry = cars.stream().filter(c -> c.carId().equals(car.getId())).findFirst().orElseThrow();
        assertThat(entry.logCount()).isEqualTo(1);
        assertThat(entry.candidates()).anySatisfy(c -> {
            assertThat(c.specId()).isEqualTo(specId);
            assertThat(c.diffKwh()).isEqualByComparingTo("1.0");
        });
    }
}
