package com.evmonitor.application.tariff.crawl;

import com.evmonitor.application.tariff.crawl.ExtractedTariff.Scope;
import com.evmonitor.domain.ChargingProviderTariff;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Seite gegen Tabelle. Ein bekannter Tarif wird über Variante, Ladeart, Geltungsbereich und
 * Leistungsklasse gefunden; ein Wert gilt nur als geändert, wenn die Seite ihn nennt. Alles
 * Zweifelhafte (mehrere Preise für einen Tarif, Sprung über 40 %, unplausible Beträge) bekommt
 * einen Haltegrund und geht an den Admin. Fehlend ist ein Tarif nur, wenn die Seite für diesen
 * Anbieter überhaupt lesbar war (mindestens ein bekannter Tarif gefunden).
 */
public final class TariffReconciler {

    static final BigDecimal MIN_PRICE = new BigDecimal("0.20");
    static final BigDecimal MAX_PRICE = new BigDecimal("1.00");
    static final BigDecimal MAX_MONTHLY_FEE = new BigDecimal("30");
    static final BigDecimal MAX_SESSION_FEE = new BigDecimal("5");
    static final BigDecimal MAX_JUMP = new BigDecimal("0.40");
    private static final BigDecimal TOLERANCE = new BigDecimal("0.0049");

    public record Result(List<ChargingProviderTariff> unchanged, List<Change> changes,
                         List<ExtractedTariff> added, List<ChargingProviderTariff> missing) {}

    /** {@code holdReason} null = darf nach Bestätigung durch einen zweiten Lauf ohne Admin übernommen werden. */
    public record Change(ChargingProviderTariff current, ExtractedTariff found, String holdReason) {}

    private TariffReconciler() {}

    public static Result reconcile(List<ChargingProviderTariff> known, List<ExtractedTariff> found) {
        Map<String, List<ExtractedTariff>> foundByKey = found.stream()
                .collect(Collectors.groupingBy(TariffReconciler::key, LinkedHashMap::new, Collectors.toList()));
        List<ChargingProviderTariff> unchanged = new ArrayList<>();
        List<Change> changes = new ArrayList<>();
        List<ChargingProviderTariff> unmatched = new ArrayList<>();
        for (ChargingProviderTariff row : known) {
            List<ExtractedTariff> candidates = foundByKey.remove(key(row));
            if (candidates == null) {
                unmatched.add(row);
                continue;
            }
            List<ExtractedTariff> distinct = distinctByPrice(candidates);
            if (distinct.size() > 1) {
                if (distinct.stream().allMatch(f -> !differs(row, f))) unchanged.add(row);
                else changes.add(new Change(row, dearest(distinct), "Seite nennt mehrere Preise: " + prices(distinct)));
                continue;
            }
            ExtractedTariff f = distinct.get(0);
            if (differs(row, f)) changes.add(new Change(row, f, holdReason(row, f)));
            else unchanged.add(row);
        }
        List<ExtractedTariff> added = foundByKey.values().stream().map(c -> dearest(distinctByPrice(c))).toList();
        boolean readable = !unchanged.isEmpty() || !changes.isEmpty();
        return new Result(unchanged, changes, added, readable ? unmatched : List.of());
    }

    /** Schlüssel eines bekannten Tarifs; muss zu {@link #key(ExtractedTariff)} passen. */
    static String key(ChargingProviderTariff t) {
        String scope;
        if (t.getCpoName() != null) scope = "OPERATOR:" + t.getCpoName().toLowerCase(Locale.ROOT);
        else if (t.getPriceTier() != null) scope = "TIER:" + t.getPriceTier();
        else if (t.isAppliesElsewhere()) scope = "ELSEWHERE";
        else scope = "OWN";
        return String.join("|", variant(t.getTariffVariant()), t.getChargingType().name(), scope, Objects.toString(t.getMinPowerKw(), "0"));
    }

    static String key(ExtractedTariff f) {
        Scope s = f.scope() == null ? Scope.OWN_NETWORK : f.scope();
        String scope = switch (s) {
            case OPERATOR -> "OPERATOR:" + Objects.toString(f.operator(), "").toLowerCase(Locale.ROOT);
            case TIER -> "TIER:" + Objects.toString(f.tier(), "");
            case ELSEWHERE -> "ELSEWHERE";
            case OWN_NETWORK -> "OWN";
        };
        return String.join("|", variant(f.variant()), Objects.toString(f.chargingType(), ""), scope, Objects.toString(f.minPowerKw(), "0"));
    }

    /** "Tariff L", "Ladetarif L" und "tarif-l" sind dieselbe Variante. */
    static String variant(String v) {
        if (v == null) return "";
        return v.toLowerCase(Locale.ROOT).replaceAll("(lade)?tarif+", "").replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static boolean differs(ChargingProviderTariff row, ExtractedTariff f) {
        return !same(row.getPricePerKwh(), f.pricePerKwh())
                || stated(f.monthlyFeeEur()) && !same(row.getMonthlyFeeEur(), f.monthlyFeeEur())
                || stated(f.sessionFeeEur()) && !same(row.getSessionFeeEur(), f.sessionFeeEur())
                || stated(f.blockingFeePerMin()) && !same(row.getBlockingFeePerMin(), f.blockingFeePerMin())
                || f.blockingFeeAfterMin() != null && !Objects.equals(row.getBlockingFeeAfterMin(), f.blockingFeeAfterMin())
                || stated(f.blockingFeeCapEur()) && !same(row.getBlockingFeeCapEur(), f.blockingFeeCapEur())
                || f.dynamic() && !row.isDynamicPricing();
    }

    private static String holdReason(ChargingProviderTariff row, ExtractedTariff f) {
        BigDecimal price = f.pricePerKwh();
        if (price.compareTo(MIN_PRICE) < 0 || price.compareTo(MAX_PRICE) > 0) return "Preis außerhalb 0,20 bis 1,00 €/kWh";
        if (stated(f.monthlyFeeEur()) && f.monthlyFeeEur().compareTo(MAX_MONTHLY_FEE) > 0) return "Grundgebühr über 30 €";
        if (stated(f.sessionFeeEur()) && f.sessionFeeEur().compareTo(MAX_SESSION_FEE) > 0) return "Startgebühr über 5 €";
        if (f.dynamic() && !row.isDynamicPricing()) return "Seite nennt dynamische Preise";
        BigDecimal old = row.getPricePerKwh();
        if (old != null && old.signum() > 0
                && price.subtract(old).abs().divide(old, 4, RoundingMode.HALF_UP).compareTo(MAX_JUMP) > 0) {
            return "Sprung über 40 %: " + format(old) + " auf " + format(price);
        }
        return null;
    }

    private static List<ExtractedTariff> distinctByPrice(List<ExtractedTariff> candidates) {
        Map<String, ExtractedTariff> byPrice = new LinkedHashMap<>();
        for (ExtractedTariff f : candidates) byPrice.putIfAbsent(f.pricePerKwh().setScale(3, RoundingMode.HALF_UP).toPlainString(), f);
        return new ArrayList<>(byPrice.values());
    }

    /** Bei Zweifel der höhere Preis: eine Ersparnis wird damit nie zu groß. */
    private static ExtractedTariff dearest(List<ExtractedTariff> fs) {
        return fs.stream().max((a, b) -> a.pricePerKwh().compareTo(b.pricePerKwh())).orElseThrow();
    }

    private static String prices(List<ExtractedTariff> fs) {
        return fs.stream().map(f -> format(f.pricePerKwh())).collect(Collectors.joining(" und "));
    }

    private static String format(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    private static boolean stated(BigDecimal v) {
        return v != null;
    }

    private static boolean same(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return a == null && b == null;
        return a.subtract(b).abs().compareTo(TOLERANCE) <= 0;
    }
}
