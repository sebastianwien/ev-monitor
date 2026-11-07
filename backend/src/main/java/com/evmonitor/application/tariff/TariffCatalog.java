package com.evmonitor.application.tariff;

import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.domain.ChargingProviderTariffRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.infrastructure.persistence.EmpEntity;
import com.evmonitor.infrastructure.persistence.JpaEmpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Die eine Stelle für "welche Katalogtarife gelten an Betreiber X, Ladeart Y, Leistung Z am Tag D".
 * Nur der gepflegte Katalog ({@code charging_provider_tariffs}), nie Fahrerdaten; dynamische Preise
 * nie, der gepflegte Wert wäre dort nur ein Beispiel. Welche Zeile gilt, entscheidet
 * {@link TariffApplicability}.
 *
 * <p>Ein {@link Snapshot} lädt den Katalog einmal und merkt sich das Tier-Mapping je Anbieter und
 * Betreiber: eine Karte mit hundert Säulen fragt jedes Paar nur einmal ab. Er gilt für eine Anfrage,
 * nicht länger - der Crawler ändert den Katalog wöchentlich.
 */
@Component
@RequiredArgsConstructor
public class TariffCatalog {

    private static final String GERMANY = "DE";

    private final ChargingProviderTariffRepository tariffs;
    private final JpaEmpRepository emps;

    /** Alle Anbieter, unabhängig vom Land - für Vergleiche an der eigenen Ladung, auch im Ausland. */
    public Snapshot on(LocalDate day) {
        return snapshot(day, Set.of());
    }

    /**
     * Für eine Säule in Deutschland: Anbieter aus anderen Ländern fallen weg, ihre Roaming-Preise
     * gelten im Katalog für ihr Heimatland. Anbieter ohne Land (international) bleiben.
     */
    public Snapshot forGermanyOn(LocalDate day) {
        Set<String> foreign = emps.findAll().stream()
                .filter(e -> e.getCountryCode() != null && !GERMANY.equals(e.getCountryCode()))
                .map(EmpEntity::getName)
                .collect(Collectors.toSet());
        return snapshot(day, foreign);
    }

    private Snapshot snapshot(LocalDate day, Set<String> excludedEmps) {
        List<ChargingProviderTariff> valid = tariffs.findValidOn(day).stream()
                .filter(t -> !t.isDynamicPricing() && !excludedEmps.contains(t.getEmpName()))
                .toList();
        Map<String, Optional<String>> tierCache = new HashMap<>();
        TariffApplicability.TierLookup tiers = (emp, operator) ->
                tierCache.computeIfAbsent(emp + "\u0000" + operator, k -> tariffs.findTierForCpo(emp, operator));
        return new Snapshot(valid, tiers);
    }

    /** Der Katalog eines Tages. Nicht threadsicher, eine Anfrage lang gültig. */
    public static final class Snapshot {
        private final List<ChargingProviderTariff> valid;
        private final TariffApplicability.TierLookup tiers;

        private Snapshot(List<ChargingProviderTariff> valid, TariffApplicability.TierLookup tiers) {
            this.valid = valid;
            this.tiers = tiers;
        }

        /** Gültige, nicht dynamische Zeilen nach Landfilter, noch ohne Betreiberbezug. */
        public List<ChargingProviderTariff> valid() {
            return valid;
        }

        public TariffApplicability.TierLookup tiers() {
            return tiers;
        }

        /**
         * Je Anbieter und Variante die geltende Zeile, günstigste zuerst, bei gleichem Preis nach Name.
         *
         * @param powerKw Leistung der Säule; null, wenn unbekannt (dann die teuerste Stufe)
         */
        public List<ChargingProviderTariff> at(String operator, ChargingType type, Integer powerKw) {
            if (operator == null || type == null || type == ChargingType.UNKNOWN) return List.of();
            return TariffApplicability.applicable(valid, new TariffApplicability.Charge(operator, type, powerKw), tiers)
                    .stream()
                    .sorted(Comparator.comparing(ChargingProviderTariff::getPricePerKwh)
                            .thenComparing(ChargingProviderTariff::getEmpName))
                    .toList();
        }
    }
}
