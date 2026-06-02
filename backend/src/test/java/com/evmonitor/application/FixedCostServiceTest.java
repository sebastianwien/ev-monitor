package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FixedCostServiceTest extends AbstractIntegrationTest {

    @Autowired
    private FixedCostService fixedCostService;

    @Autowired
    private FixedCostRepository fixedCostRepository;

    private UUID userId;
    private UUID carId;
    private String userEmail;

    @BeforeEach
    void setUp() {
        userEmail = "fixedcost-" + System.currentTimeMillis() + "@example.com";
        User user = createAndSaveUser(userEmail);
        userId = user.getId();
        Car car = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3);
        carId = car.getId();
    }

    // --- calculateForPeriod: ONE_TIME ---

    @Test
    void calculateForPeriod_oneTime_inPeriod_includesCost() {
        FixedCost fc = FixedCost.createNew(carId, userId, "Maut", new BigDecimal("11.50"),
                FixedCostCategory.TOLL, FixedCostRecurrence.ONE_TIME,
                LocalDate.of(2024, 6, 15), null, null);
        fixedCostRepository.save(fc);

        BigDecimal result = fixedCostService.calculateForPeriod(carId,
                LocalDate.of(2024, 6, 1), LocalDate.of(2024, 6, 30));

        assertEquals(new BigDecimal("11.50"), result);
    }

    @Test
    void calculateForPeriod_oneTime_outsidePeriod_excludesCost() {
        FixedCost fc = FixedCost.createNew(carId, userId, "Maut", new BigDecimal("11.50"),
                FixedCostCategory.TOLL, FixedCostRecurrence.ONE_TIME,
                LocalDate.of(2024, 5, 10), null, null);
        fixedCostRepository.save(fc);

        BigDecimal result = fixedCostService.calculateForPeriod(carId,
                LocalDate.of(2024, 6, 1), LocalDate.of(2024, 6, 30));

        assertEquals(BigDecimal.ZERO, result);
    }

    // --- calculateForPeriod: MONTHLY ---

    @Test
    void calculateForPeriod_monthly_fullPeriod_countsAllMonths() {
        // Versicherung 89 EUR/Monat, April bis Juni = 3 Monate
        FixedCost fc = FixedCost.createNew(carId, userId, "Versicherung", new BigDecimal("89.00"),
                FixedCostCategory.INSURANCE, FixedCostRecurrence.MONTHLY,
                null, LocalDate.of(2024, 4, 1), null);
        fixedCostRepository.save(fc);

        BigDecimal result = fixedCostService.calculateForPeriod(carId,
                LocalDate.of(2024, 4, 1), LocalDate.of(2024, 6, 30));

        assertEquals(new BigDecimal("267.00"), result);
    }

    @Test
    void calculateForPeriod_monthly_endDateBeforePeriodEnd_stopsAtEndDate() {
        // Versicherung lief bis 31.05 - nur 2 Monate im April-Juni-Zeitraum
        FixedCost fc = FixedCost.createNew(carId, userId, "Versicherung", new BigDecimal("89.00"),
                FixedCostCategory.INSURANCE, FixedCostRecurrence.MONTHLY,
                null, LocalDate.of(2024, 4, 1), LocalDate.of(2024, 5, 31));
        fixedCostRepository.save(fc);

        BigDecimal result = fixedCostService.calculateForPeriod(carId,
                LocalDate.of(2024, 4, 1), LocalDate.of(2024, 6, 30));

        assertEquals(new BigDecimal("178.00"), result);
    }

    @Test
    void calculateForPeriod_monthly_startDateAfterPeriodStart_startsLate() {
        // Versicherung startet erst am 15. Mai, Zeitraum ist April-Juni -> 2 Monate (Mai, Juni)
        FixedCost fc = FixedCost.createNew(carId, userId, "Versicherung", new BigDecimal("89.00"),
                FixedCostCategory.INSURANCE, FixedCostRecurrence.MONTHLY,
                null, LocalDate.of(2024, 5, 15), null);
        fixedCostRepository.save(fc);

        BigDecimal result = fixedCostService.calculateForPeriod(carId,
                LocalDate.of(2024, 4, 1), LocalDate.of(2024, 6, 30));

        assertEquals(new BigDecimal("178.00"), result);
    }

    // --- calculateForPeriod: QUARTERLY ---

    @Test
    void calculateForPeriod_quarterly_twoQuartersInPeriod() {
        // Steuer 300 EUR/Quartal, Q1 + Q2 2024 = 2 Treffer
        FixedCost fc = FixedCost.createNew(carId, userId, "Kfz-Steuer", new BigDecimal("300.00"),
                FixedCostCategory.TAX, FixedCostRecurrence.QUARTERLY,
                null, LocalDate.of(2024, 1, 1), null);
        fixedCostRepository.save(fc);

        BigDecimal result = fixedCostService.calculateForPeriod(carId,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 30));

        assertEquals(new BigDecimal("600.00"), result);
    }

    // --- calculateForPeriod: YEARLY ---

    @Test
    void calculateForPeriod_yearly_fullYear_returnsFull() {
        FixedCost fc = FixedCost.createNew(carId, userId, "Jahressteuer", new BigDecimal("1200.00"),
                FixedCostCategory.TAX, FixedCostRecurrence.YEARLY,
                null, LocalDate.of(2024, 1, 1), null);
        fixedCostRepository.save(fc);

        BigDecimal result = fixedCostService.calculateForPeriod(carId,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));

        // 1200 / 12 * 12 = 1200
        assertEquals(new BigDecimal("1200.00"), result);
    }

    @Test
    void calculateForPeriod_yearly_halfYear_returnsHalf() {
        // THIS_YEAR: 1600 EUR Jahresversicherung, Jan-Jun = 6 Monate → 1600/12*6 = 800
        FixedCost fc = FixedCost.createNew(carId, userId, "Versicherung", new BigDecimal("1600.00"),
                FixedCostCategory.INSURANCE, FixedCostRecurrence.YEARLY,
                null, LocalDate.of(2026, 1, 1), null);
        fixedCostRepository.save(fc);

        BigDecimal result = fixedCostService.calculateForPeriod(carId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));

        assertEquals(new BigDecimal("800.00"), result);
    }

    @Test
    void calculateForPeriod_yearly_startBeforePeriod_proratesFromPeriodStart() {
        // startDate liegt vor dem Zeitraum → effektiver Start = Zeitraum-Start
        FixedCost fc = FixedCost.createNew(carId, userId, "Jahressteuer", new BigDecimal("1200.00"),
                FixedCostCategory.TAX, FixedCostRecurrence.YEARLY,
                null, LocalDate.of(2023, 1, 1), null);
        fixedCostRepository.save(fc);

        BigDecimal result = fixedCostService.calculateForPeriod(carId,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));

        // 1200 / 12 * 12 = 1200
        assertEquals(new BigDecimal("1200.00"), result);
    }

    @Test
    void calculateForPeriod_yearly_endDateCutsShort_prorated() {
        // Versicherung läuft bis 30.09 → nur 9 Monate im Jahres-Zeitraum
        FixedCost fc = FixedCost.createNew(carId, userId, "Versicherung", new BigDecimal("1200.00"),
                FixedCostCategory.INSURANCE, FixedCostRecurrence.YEARLY,
                null, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 9, 30));
        fixedCostRepository.save(fc);

        BigDecimal result = fixedCostService.calculateForPeriod(carId,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));

        // 1200 / 12 * 9 = 900
        assertEquals(new BigDecimal("900.00"), result);
    }

    // --- CRUD ---

    @Test
    void create_savesAndReturns() {
        FixedCostRequest req = new FixedCostRequest("Wäsche", new BigDecimal("12.00"),
                FixedCostCategory.CLEANING, FixedCostRecurrence.ONE_TIME,
                LocalDate.of(2024, 3, 1), null, null);

        FixedCostResponse response = fixedCostService.create(carId, userId, req);

        assertNotNull(response.id());
        assertEquals("Wäsche", response.description());
        assertEquals(new BigDecimal("12.00"), response.amount());
    }

    @Test
    void create_otherUsersCar_throws() {
        UUID otherUserId = createAndSaveUser("other-" + System.currentTimeMillis() + "@example.com").getId();
        FixedCostRequest req = new FixedCostRequest("Maut", new BigDecimal("10.00"),
                FixedCostCategory.TOLL, FixedCostRecurrence.ONE_TIME,
                LocalDate.of(2024, 3, 1), null, null);

        assertThrows(Exception.class, () -> fixedCostService.create(carId, otherUserId, req));
    }

    @Test
    void list_returnsOnlyCarEntries() {
        UUID otherCarId = createAndSaveCar(userId, CarBrand.CarModel.MODEL_Y).getId();
        fixedCostRepository.save(FixedCost.createNew(carId, userId, "A", BigDecimal.ONE,
                FixedCostCategory.OTHER, FixedCostRecurrence.ONE_TIME,
                LocalDate.now(), null, null));
        fixedCostRepository.save(FixedCost.createNew(otherCarId, userId, "B", BigDecimal.ONE,
                FixedCostCategory.OTHER, FixedCostRecurrence.ONE_TIME,
                LocalDate.now(), null, null));

        List<FixedCostResponse> result = fixedCostService.list(carId, userId);

        assertEquals(1, result.size());
        assertEquals("A", result.get(0).description());
    }

    @Test
    void delete_ownEntry_succeeds() {
        FixedCost fc = fixedCostRepository.save(FixedCost.createNew(carId, userId, "Reinigen",
                new BigDecimal("8.00"), FixedCostCategory.CLEANING, FixedCostRecurrence.ONE_TIME,
                LocalDate.now(), null, null));

        fixedCostService.delete(fc.getId(), userId);

        assertTrue(fixedCostRepository.findById(fc.getId()).isEmpty());
    }

    @Test
    void delete_otherUsersEntry_throws() {
        UUID otherUserId = createAndSaveUser("del-other-" + System.currentTimeMillis() + "@example.com").getId();
        FixedCost fc = fixedCostRepository.save(FixedCost.createNew(carId, userId, "Reinigen",
                new BigDecimal("8.00"), FixedCostCategory.CLEANING, FixedCostRecurrence.ONE_TIME,
                LocalDate.now(), null, null));

        assertThrows(Exception.class, () -> fixedCostService.delete(fc.getId(), otherUserId));
    }
}
