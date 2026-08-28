package com.evmonitor.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class FixedCost {
    private final UUID id;
    private final UUID carId;
    private final UUID userId;
    private final String description;
    private final BigDecimal amount;
    private final FixedCostCategory category;
    private final FixedCostRecurrence recurrence;
    /** Set for ONE_TIME entries. */
    private final LocalDate date;
    /** Set for recurring entries. */
    private final LocalDate startDate;
    /** Null means "still active" for recurring entries. */
    private final LocalDate endDate;
    private final LocalDateTime createdAt;

    public static FixedCost createNew(UUID carId, UUID userId, String description,
            BigDecimal amount, FixedCostCategory category, FixedCostRecurrence recurrence,
            LocalDate date, LocalDate startDate, LocalDate endDate) {
        return FixedCost.builder()
                .id(UUID.randomUUID())
                .carId(carId)
                .userId(userId)
                .description(description)
                .amount(normalizeAmount(amount, category))
                .category(category)
                .recurrence(recurrence)
                .date(date)
                .startDate(startDate)
                .endDate(endDate)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * Einnahmen werden immer negativ gespeichert - egal ob der Aufrufer den Betrag positiv
     * oder bereits negativ liefert. So bleibt die Invariante unabhaengig vom Client.
     */
    public static BigDecimal normalizeAmount(BigDecimal amount, FixedCostCategory category) {
        if (amount == null || category == null) return amount;
        return category.isIncome() ? amount.abs().negate() : amount;
    }
}
