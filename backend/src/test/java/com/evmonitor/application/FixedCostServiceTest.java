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

// Helper to compare BigDecimal values ignoring scale (e.g. 0 == 0.00)
class BigDecimalAssert {
    static void assertEq(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual),
                () -> "expected: <" + expected + "> but was: <" + actual + ">");
    }
}

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

        BigDecimalAssert.assertEq(BigDecimal.ZERO, result);
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
        FixedCost fc = FixedCost.createNew(carId, userId, "Jahressteuer", new BigDecimal("500.00"),
                FixedCostCategory.TAX, FixedCostRecurrence.YEARLY,
                null, LocalDate.of(2024, 1, 1), null);
        fixedCostRepository.save(fc);

        BigDecimal result = fixedCostService.calculateForPeriod(carId,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));

        // Jahrestag 2024-01-01 liegt im Zeitraum → 1 Treffer = 500
        assertEquals(new BigDecimal("500.00"), result);
    }

    @Test
    void calculateForPeriod_yearly_halfYear_returnsZero() {
        // Jahrestag 2026-01-01, Zeitraum Feb-Jun → kein Treffer
        FixedCost fc = FixedCost.createNew(carId, userId, "Versicherung", new BigDecimal("1600.00"),
                FixedCostCategory.INSURANCE, FixedCostRecurrence.YEARLY,
                null, LocalDate.of(2026, 1, 1), null);
        fixedCostRepository.save(fc);

        BigDecimal result = fixedCostService.calculateForPeriod(carId,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 6, 30));

        BigDecimalAssert.assertEq(BigDecimal.ZERO, result);
    }

    @Test
    void calculateForPeriod_yearly_startBeforePeriod_hitsInPeriod() {
        // startDate 2023-01-01, Zeitraum 2024-01-01 bis 2024-12-31 → Jahrestag 2024-01-01 trifft
        FixedCost fc = FixedCost.createNew(carId, userId, "Jahressteuer", new BigDecimal("500.00"),
                FixedCostCategory.TAX, FixedCostRecurrence.YEARLY,
                null, LocalDate.of(2023, 1, 1), null);
        fixedCostRepository.save(fc);

        BigDecimal result = fixedCostService.calculateForPeriod(carId,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));

        assertEquals(new BigDecimal("500.00"), result);
    }

    @Test
    void calculateForPeriod_yearly_endDateCutsShort_noHit() {
        // Versicherung läuft bis 30.09, Jahrestag wäre 01.01 nächstes Jahr → kein Treffer
        FixedCost fc = FixedCost.createNew(carId, userId, "Versicherung", new BigDecimal("1200.00"),
                FixedCostCategory.INSURANCE, FixedCostRecurrence.YEARLY,
                null, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 9, 30));
        fixedCostRepository.save(fc);

        BigDecimal result = fixedCostService.calculateForPeriod(carId,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

        BigDecimalAssert.assertEq(BigDecimal.ZERO, result);
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

    // --- negative amounts (income / compensation) ---

    @Test
    void calculateForPeriod_negativeOneTime_reducesTotal() {
        // THG-Quote als Verguetung: negativer Betrag zieht von den Fixkosten ab
        fixedCostRepository.save(FixedCost.createNew(carId, userId, "Versicherung",
                new BigDecimal("89.00"), FixedCostCategory.INSURANCE, FixedCostRecurrence.ONE_TIME,
                LocalDate.of(2024, 6, 15), null, null));
        fixedCostRepository.save(FixedCost.createNew(carId, userId, "THG-Quote",
                new BigDecimal("-120.00"), FixedCostCategory.COMPENSATION, FixedCostRecurrence.ONE_TIME,
                LocalDate.of(2024, 6, 20), null, null));

        BigDecimal result = fixedCostService.calculateForPeriod(carId,
                LocalDate.of(2024, 6, 1), LocalDate.of(2024, 6, 30));

        BigDecimalAssert.assertEq(new BigDecimal("-31.00"), result);
    }

    @Test
    void calculateForPeriod_negativeRecurring_multipliesAcrossMonths() {
        // Monatliche Einnahme 25 EUR ueber drei Monate
        fixedCostRepository.save(FixedCost.createNew(carId, userId, "Stellplatz-Untermiete",
                new BigDecimal("-25.00"), FixedCostCategory.INCOME, FixedCostRecurrence.MONTHLY,
                null, LocalDate.of(2024, 4, 1), null));

        BigDecimal result = fixedCostService.calculateForPeriod(carId,
                LocalDate.of(2024, 4, 1), LocalDate.of(2024, 6, 30));

        BigDecimalAssert.assertEq(new BigDecimal("-75.00"), result);
    }

    @Test
    void create_negativeAmount_isPersisted() {
        FixedCostRequest request = new FixedCostRequest("THG-Quote", new BigDecimal("-350.00"),
                FixedCostCategory.COMPENSATION, FixedCostRecurrence.YEARLY,
                null, LocalDate.of(2024, 1, 1), null);

        FixedCostResponse result = fixedCostService.create(carId, userId, request);

        BigDecimalAssert.assertEq(new BigDecimal("-350.00"), result.amount());
        assertEquals(FixedCostCategory.COMPENSATION, result.category());
    }
}
