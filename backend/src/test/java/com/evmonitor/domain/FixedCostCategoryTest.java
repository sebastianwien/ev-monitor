package com.evmonitor.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FixedCostCategoryTest {

    @Test
    void incomeCategories_areMarkedAsIncome() {
        assertTrue(FixedCostCategory.INCOME.isIncome());
        assertTrue(FixedCostCategory.COMPENSATION.isIncome());
    }

    @Test
    void costCategories_areNotMarkedAsIncome() {
        assertFalse(FixedCostCategory.INSURANCE.isIncome());
        assertFalse(FixedCostCategory.LEASING.isIncome());
        assertFalse(FixedCostCategory.OTHER.isIncome());
    }

    @Test
    void createNew_incomeCategory_normalizesPositiveAmountToNegative() {
        FixedCost fc = createWithAmount(new BigDecimal("350.00"), FixedCostCategory.COMPENSATION);

        assertEquals(0, new BigDecimal("-350.00").compareTo(fc.getAmount()));
    }

    @Test
    void createNew_incomeCategory_keepsAlreadyNegativeAmount() {
        FixedCost fc = createWithAmount(new BigDecimal("-350.00"), FixedCostCategory.INCOME);

        assertEquals(0, new BigDecimal("-350.00").compareTo(fc.getAmount()));
    }

    @Test
    void createNew_costCategory_leavesAmountUntouched() {
        assertEquals(0, new BigDecimal("89.00").compareTo(
                createWithAmount(new BigDecimal("89.00"), FixedCostCategory.INSURANCE).getAmount()));
        // negative Kostenposition (Rueckerstattung) bleibt erhalten
        assertEquals(0, new BigDecimal("-40.00").compareTo(
                createWithAmount(new BigDecimal("-40.00"), FixedCostCategory.INSURANCE).getAmount()));
    }

    @Test
    void createNew_nullAmount_doesNotThrow() {
        assertNull(createWithAmount(null, FixedCostCategory.INCOME).getAmount());
    }

    private FixedCost createWithAmount(BigDecimal amount, FixedCostCategory category) {
        return FixedCost.createNew(UUID.randomUUID(), UUID.randomUUID(), "test", amount, category,
                FixedCostRecurrence.ONE_TIME, LocalDate.of(2024, 6, 1), null, null);
    }
}
