package com.evmonitor.application.tariff.savings;

import com.evmonitor.application.tariff.TariffApplicability;
import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.domain.ChargingType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Was eine Ladekarte bei einer Ladung gespart hätte.
 *
 * <p>Kartenkosten = kWh an der Säule mal Kartenpreis, plus Startgebühr, plus Blockiergebühr
 * (Minuten über der Schwelle mal Minutenpreis, höchstens der Deckel). Ist eine Blockiergebühr
 * bekannt, die Ladedauer aber nicht, bleibt die Karte weg: die Rechnung wäre geraten.
 *
 * <p>Die Grundgebühr wird auf die kWh verteilt, bei denen die Karte in den 30 Tagen bis zu dieser
 * Ladung Geld gespart hätte (dort, wo sie teurer wäre, nimmt man sie nicht). Eine Karte erscheint
 * nur, wenn sie bei dieser Ladung und über den Monat mindestens einen Euro spart; je Anbieter nur die
 * beste Variante, höchstens {@link #MAX_OFFERS} Karten.
 */
public final class CardSavingsCalculator {

    static final BigDecimal MIN_SAVING = BigDecimal.ONE;
    /** Ein Vergleich, keine Werbeliste: je Anbieter die beste Variante, höchstens drei Karten. */
    static final int MAX_OFFERS = 3;

    private CardSavingsCalculator() {}

    /** Eine öffentliche Ladung, schon geprüft: Betreiber im Katalog, Säulen-kWh, Kosten in Euro, in Deutschland. */
    public record Charge(UUID logId, String operator, ChargingType type, Integer powerKw, BigDecimal kwh, BigDecimal costEur,
                         Integer durationMinutes, LocalDate day) {}

    /** Beträge auf Cent gerundet; {@code feeShareEur} ist der Anteil der Grundgebühr, der auf diese Ladung fällt. */
    public record Offer(String name, String variant, BigDecimal pricePerKwh, BigDecimal monthlyFeeEur, LocalDate verifiedOn,
                        BigDecimal chargeSavingsEur, BigDecimal feeShareEur, BigDecimal monthSavingsEur) {}

    /** {@code month}: Ladungen der 30 Tage bis zu dieser, sie selbst eingeschlossen. Größte Ersparnis zuerst. */
    public static List<Offer> offers(Charge charge, List<Charge> month, List<ChargingProviderTariff> tariffs,
                                     TariffApplicability.TierLookup tiers) {
        List<Offer> offers = new ArrayList<>();
        for (ChargingProviderTariff card : TariffApplicability.applicable(tariffs, at(charge), tiers)) {
            Optional<BigDecimal> gross = saving(charge, card);
            if (gross.isEmpty() || gross.get().signum() <= 0) continue;
            List<ChargingProviderTariff> sameCard = tariffs.stream()
                    .filter(t -> t.getEmpName().equals(card.getEmpName()) && java.util.Objects.equals(t.getTariffVariant(), card.getTariffVariant()))
                    .toList();
            BigDecimal kwhBase = BigDecimal.ZERO;
            BigDecimal monthGross = BigDecimal.ZERO;
            for (Charge m : month) {
                Optional<BigDecimal> s = TariffApplicability.applicable(sameCard, at(m), tiers).stream().findFirst()
                        .flatMap(row -> saving(m, row));
                if (s.isEmpty() || s.get().signum() <= 0) continue;
                kwhBase = kwhBase.add(m.kwh());
                monthGross = monthGross.add(s.get());
            }
            if (!month.contains(charge)) {
                kwhBase = kwhBase.add(charge.kwh());
                monthGross = monthGross.add(gross.get());
            }
            BigDecimal fee = card.getMonthlyFeeEur() == null ? BigDecimal.ZERO : card.getMonthlyFeeEur();
            BigDecimal feeShare = fee.multiply(charge.kwh()).divide(kwhBase, 6, RoundingMode.HALF_UP);
            BigDecimal net = gross.get().subtract(feeShare);
            BigDecimal monthNet = monthGross.subtract(fee);
            if (net.compareTo(MIN_SAVING) < 0 || monthNet.compareTo(MIN_SAVING) < 0) continue;
            offers.add(new Offer(card.getEmpName(), card.getTariffVariant(), card.getPricePerKwh(), fee,
                    card.getLastVerifiedAt() == null ? null : card.getLastVerifiedAt().toLocalDate(),
                    cents(net), cents(feeShare), cents(monthNet)));
        }
        offers.sort(Comparator.comparing(Offer::chargeSavingsEur).reversed().thenComparing(Offer::name));
        java.util.Set<String> seen = new java.util.HashSet<>();
        return offers.stream().filter(o -> seen.add(o.name())).limit(MAX_OFFERS).toList();
    }

    /** Gezahlt minus Kartenkosten; leer, wenn die Kartenkosten nicht bestimmbar sind. */
    static Optional<BigDecimal> saving(Charge c, ChargingProviderTariff card) {
        BigDecimal cost = c.kwh().multiply(card.getPricePerKwh());
        if (card.getSessionFeeEur() != null) cost = cost.add(card.getSessionFeeEur());
        BigDecimal perMin = card.getBlockingFeePerMin();
        if (perMin != null && perMin.signum() > 0) {
            if (c.durationMinutes() == null) return Optional.empty();
            int after = card.getBlockingFeeAfterMin() == null ? 0 : card.getBlockingFeeAfterMin();
            BigDecimal blocking = perMin.multiply(BigDecimal.valueOf(Math.max(0, c.durationMinutes() - after)));
            if (card.getBlockingFeeCapEur() != null) blocking = blocking.min(card.getBlockingFeeCapEur());
            cost = cost.add(blocking);
        }
        return Optional.of(c.costEur().subtract(cost));
    }

    private static TariffApplicability.Charge at(Charge c) {
        return new TariffApplicability.Charge(c.operator(), c.type(), c.powerKw());
    }

    private static BigDecimal cents(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
