package com.evmonitor.application.tariff;

import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.domain.ChargingType;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Welche Zeile einer Karte an einem Betreiber gilt: je Anbieter und Variante die genaueste.
 * Benannter Betreiber vor Tier, Tier vor eigenem Netz, eigenes Netz vor "an allen anderen".
 * Ein Tier gilt nur, wo das Tier-Mapping des Anbieters ihn nennt.
 *
 * <p>Mehrere Zeilen nach Leistung (DC bis 50 kW, über 50 kW): mit bekannter Leistung die Zeile
 * mit der höchsten Untergrenze darunter, ohne Leistung die teuerste. So wird eine Ersparnis nie
 * zu groß gerechnet.
 */
public final class TariffApplicability {

    /** Tier eines Betreibers bei einem Anbieter laut Mapping, leer ohne Eintrag. */
    @FunctionalInterface
    public interface TierLookup {
        Optional<String> tierOf(String empName, String operator);
    }

    /** Die Ladung, für die ein Preis gesucht wird; {@code powerKw} null, wenn unbekannt. */
    public record Charge(String operator, ChargingType type, Integer powerKw) {}

    private static final int OPERATOR = 4;
    private static final int TIER = 3;
    private static final int OWN_NETWORK = 2;
    private static final int ELSEWHERE = 1;
    private static final int NONE = 0;

    private TariffApplicability() {}

    /** Je Anbieter und Variante höchstens eine Zeile, in der Reihenfolge des ersten Auftretens. */
    public static List<ChargingProviderTariff> applicable(List<ChargingProviderTariff> tariffs, Charge charge, TierLookup tiers) {
        Map<String, List<ChargingProviderTariff>> byCard = new LinkedHashMap<>();
        for (ChargingProviderTariff t : tariffs) {
            if (t.getChargingType() != charge.type() || t.getPricePerKwh() == null) continue;
            byCard.computeIfAbsent(t.getEmpName() + "\u0000" + Objects.toString(t.getTariffVariant(), ""), k -> new java.util.ArrayList<>()).add(t);
        }
        return byCard.values().stream()
                .map(rows -> best(rows, charge, tiers))
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<ChargingProviderTariff> best(List<ChargingProviderTariff> rows, Charge charge, TierLookup tiers) {
        for (int rank = OPERATOR; rank > NONE; rank--) {
            final int wanted = rank;
            List<ChargingProviderTariff> ofRank = rows.stream().filter(t -> rank(t, charge.operator(), tiers) == wanted).toList();
            Optional<ChargingProviderTariff> byPower = forPower(ofRank, charge.powerKw());
            if (byPower.isPresent()) return byPower;
        }
        return Optional.empty();
    }

    private static int rank(ChargingProviderTariff t, String operator, TierLookup tiers) {
        if (t.getCpoName() != null) return t.getCpoName().equals(operator) ? OPERATOR : NONE;
        if (t.getPriceTier() != null) {
            Optional<String> mapped = tiers.tierOf(t.getEmpName(), operator);
            if (mapped.isPresent()) return mapped.get().equals(t.getPriceTier()) ? TIER : NONE;
            return t.isAppliesElsewhere() ? ELSEWHERE : NONE;
        }
        if (t.isAppliesElsewhere()) return ELSEWHERE;
        return t.getEmpName().equals(operator) ? OWN_NETWORK : NONE;
    }

    private static Optional<ChargingProviderTariff> forPower(List<ChargingProviderTariff> rows, Integer powerKw) {
        if (rows.isEmpty()) return Optional.empty();
        if (powerKw == null) return rows.stream().max(Comparator.comparing(ChargingProviderTariff::getPricePerKwh));
        return rows.stream()
                .filter(t -> minPower(t) < powerKw)
                .max(Comparator.comparingInt(TariffApplicability::minPower));
    }

    private static int minPower(ChargingProviderTariff t) {
        return t.getMinPowerKw() == null ? 0 : t.getMinPowerKw();
    }
}
