package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.domain.exception.ForbiddenException;
import com.evmonitor.domain.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FixedCostService {

    private final FixedCostRepository fixedCostRepository;
    private final CarRepository carRepository;

    @Transactional
    public FixedCostResponse create(UUID carId, UUID userId, FixedCostRequest request) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> NotFoundException.forEntity("Car", carId));
        if (!car.getUserId().equals(userId)) {
            throw ForbiddenException.notOwner("Car", carId);
        }
        FixedCost fc = FixedCost.createNew(carId, userId, request.description(), request.amount(),
                request.category(), request.recurrence(), request.date(),
                request.startDate(), request.endDate());
        return FixedCostResponse.fromDomain(fixedCostRepository.save(fc));
    }

    @Transactional
    public FixedCostResponse update(UUID id, UUID userId, FixedCostRequest request) {
        FixedCost fc = fixedCostRepository.findById(id)
                .orElseThrow(() -> NotFoundException.forEntity("FixedCost", id));
        if (!fc.getUserId().equals(userId)) {
            throw ForbiddenException.notOwner("FixedCost", id);
        }
        FixedCost updated = fc.toBuilder()
                .description(request.description())
                .amount(request.amount())
                .category(request.category())
                .recurrence(request.recurrence())
                .date(request.date())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .build();
        return FixedCostResponse.fromDomain(fixedCostRepository.save(updated));
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        FixedCost fc = fixedCostRepository.findById(id)
                .orElseThrow(() -> NotFoundException.forEntity("FixedCost", id));
        if (!fc.getUserId().equals(userId)) {
            throw ForbiddenException.notOwner("FixedCost", id);
        }
        fixedCostRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<FixedCostResponse> list(UUID carId, UUID userId) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> NotFoundException.forEntity("Car", carId));
        if (!car.getUserId().equals(userId)) {
            throw ForbiddenException.notOwner("Car", carId);
        }
        return fixedCostRepository.findAllByCarId(carId).stream()
                .map(FixedCostResponse::fromDomain)
                .toList();
    }

    /**
     * Calculate total fixed costs for a car within [from, to] (inclusive).
     *
     * ONE_TIME: counted if `date` falls within [from, to].
     * MONTHLY: one occurrence per calendar month that overlaps the effective period.
     * QUARTERLY: one occurrence per calendar quarter whose first month overlaps effective period.
     * YEARLY: one occurrence per calendar year whose anniversary falls within [from, to].
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateForPeriod(UUID carId, LocalDate from, LocalDate to) {
        return fixedCostRepository.findAllByCarId(carId).stream()
                .map(fc -> amountForPeriod(fc, from, to))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal amountForPeriod(FixedCost fc, LocalDate from, LocalDate to) {
        return switch (fc.getRecurrence()) {
            case ONE_TIME -> oneTimeAmount(fc, from, to);
            case MONTHLY -> recurringAmount(fc, from, to, 1);
            case QUARTERLY -> recurringAmount(fc, from, to, 3);
            case YEARLY -> recurringAmount(fc, from, to, 12);
        };
    }

    private BigDecimal oneTimeAmount(FixedCost fc, LocalDate from, LocalDate to) {
        LocalDate d = fc.getDate();
        if (d == null) return BigDecimal.ZERO;
        return (!d.isBefore(from) && !d.isAfter(to)) ? fc.getAmount() : BigDecimal.ZERO;
    }

    /**
     * Count how many recurrence intervals land within [from, to], respecting start_date / end_date.
     * intervalMonths: 1=monthly, 3=quarterly, 12=yearly.
     */
    private BigDecimal recurringAmount(FixedCost fc, LocalDate from, LocalDate to, int intervalMonths) {
        LocalDate effectiveFrom = fc.getStartDate().isAfter(from) ? fc.getStartDate() : from;
        LocalDate effectiveTo = (fc.getEndDate() != null && fc.getEndDate().isBefore(to))
                ? fc.getEndDate() : to;

        if (effectiveFrom.isAfter(effectiveTo)) return BigDecimal.ZERO;

        long count = countIntervalHits(fc.getStartDate(), effectiveFrom, effectiveTo, intervalMonths);
        return fc.getAmount().multiply(BigDecimal.valueOf(count));
    }

    /**
     * Count how many multiples of intervalMonths from the anchor date land in [from, to].
     */
    private long countIntervalHits(LocalDate anchor, LocalDate from, LocalDate to, int intervalMonths) {
        long count = 0;
        LocalDate cursor = anchor;
        while (cursor.isBefore(from)) {
            cursor = cursor.plusMonths(intervalMonths);
        }
        while (!cursor.isAfter(to)) {
            count++;
            cursor = cursor.plusMonths(intervalMonths);
        }
        return count;
    }
}
