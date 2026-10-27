package com.evmonitor.application.tariff.savings;

import com.evmonitor.application.publicapi.CpoNameNormalizer;
import com.evmonitor.application.tariff.TariffApplicability;
import com.evmonitor.application.tariff.savings.CardSavingsCalculator.Charge;
import com.evmonitor.application.tariff.savings.CardSavingsCalculator.Offer;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.domain.ChargingProviderTariffRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.exception.ForbiddenException;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.infrastructure.persistence.JpaEmpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * "Mit dieser Karte hättest du X € gespart" für eine eigene Ladung. Zählt nur, was sich belegen
 * lässt: Ladung in Deutschland (Geohash), Betreiber im Katalog, kWh an der Säule, Kosten; Tarife
 * gepflegt, nicht dynamisch, nicht aus einem anderen Land. Leer heißt: kein Euro-Satz.
 */
@Service
@RequiredArgsConstructor
public class CardSavingsService {

    static final int MONTH_DAYS = 30;

    private final EvLogRepository logs;
    private final CarRepository cars;
    private final ChargingProviderTariffRepository tariffs;
    private final CpoNameNormalizer operators;
    private final JpaEmpRepository emps;

    /** Was der Nutzer gezahlt hat und welche Karten bei dieser Ladung mindestens einen Euro gespart hätten. */
    public record CardSavings(UUID logId, String cpoName, ChargingType chargingType, BigDecimal paidPerKwh,
                              BigDecimal kwh, List<Offer> offers) {}

    @Transactional(readOnly = true)
    public Optional<CardSavings> forLog(UUID logId, UUID userId) {
        EvLog log = logs.findById(logId).orElseThrow(() -> NotFoundException.forEntity("EvLog", logId));
        Car car = cars.findById(log.getCarId()).orElseThrow(() -> NotFoundException.forEntity("Car", log.getCarId()));
        if (!car.isOwnedBy(userId)) throw ForbiddenException.notOwner("EvLog", logId);

        Charge charge = toCharge(log);
        if (charge == null) return Optional.empty();
        LocalDateTime from = log.getLoggedAt().minusDays(MONTH_DAYS);
        List<Charge> month = logs.findAllByCarId(car.getId()).stream()
                .filter(l -> !l.getLoggedAt().isBefore(from) && !l.getLoggedAt().isAfter(log.getLoggedAt()))
                .map(this::toCharge)
                .filter(Objects::nonNull)
                .toList();
        Set<String> foreign = emps.findAll().stream()
                .filter(e -> e.getCountryCode() != null && !"DE".equals(e.getCountryCode()))
                .map(e -> e.getName())
                .collect(Collectors.toSet());
        List<ChargingProviderTariff> valid = tariffs.findValidOn(charge.day()).stream()
                .filter(t -> !t.isDynamicPricing() && !foreign.contains(t.getEmpName()))
                .toList();
        Map<String, Optional<String>> tierCache = new HashMap<>();
        TariffApplicability.TierLookup tiers = (emp, op) ->
                tierCache.computeIfAbsent(emp + "\u0000" + op, k -> tariffs.findTierForCpo(emp, op));

        List<Offer> offers = CardSavingsCalculator.offers(charge, month, valid, tiers);
        if (offers.isEmpty()) return Optional.empty();
        BigDecimal paid = charge.costEur().divide(charge.kwh(), 3, java.math.RoundingMode.HALF_UP);
        return Optional.of(new CardSavings(logId, charge.operator(), charge.type(), paid, charge.kwh(), offers));
    }

    /** null, wenn die Ladung für eine belegbare Rechnung nicht taugt. */
    private Charge toCharge(EvLog l) {
        if (l.getDeletedAt() != null || !Boolean.TRUE.equals(l.getPublicCharging())) return null;
        if (l.getChargingType() != ChargingType.AC && l.getChargingType() != ChargingType.DC) return null;
        if (l.getKwhCharged() == null || l.getKwhCharged().signum() <= 0) return null;
        if (l.getCostEur() == null || l.getCostEur().signum() <= 0) return null;
        String operator = operators.canonicalOperator(l.getCpoName());
        if (operator == null || !GermanyBorder.containsGeohash(l.getGeohash())) return null;
        Integer power = l.getMaxChargingPowerKw() == null ? null : l.getMaxChargingPowerKw().intValue();
        return new Charge(l.getId(), operator, l.getChargingType(), power, l.getKwhCharged(), l.getCostEur(),
                l.getChargeDurationMinutes(), l.getLoggedAt().toLocalDate());
    }
}
