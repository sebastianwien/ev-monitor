package com.evmonitor.application.vehiclespec;

import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.VehicleSpecification;
import com.evmonitor.domain.exception.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Fachregeln für Spec-Zeilen. Harte Regeln werfen {@link ValidationException} (400 mit Feldtext),
 * Warnungen erlauben das Speichern und landen im Änderungsprotokoll.
 */
final class SpecRules {

    /** Nutzer-Zeilen aus dem alten Formular hießen "Brutto/Netto kWh". */
    static final Pattern USER_ROW_NAME = Pattern.compile("^[0-9.,]+/[0-9.,]+ kWh$");
    /** Unter diesem Verhältnis (WLTP-km × Verbrauch / 100 / Netto) sieht die Reichweite nach Realwert statt WLTP aus. */
    static final BigDecimal MIN_RATIO = new BigDecimal("1.10");
    static final BigDecimal MAX_DC_KW = new BigDecimal("1000");

    private SpecRules() {
    }

    static String sanitize(String input) {
        if (input == null) return "";
        return input.replaceAll("<[^>]*>", "").trim();
    }

    static void requireHard(SpecCommand cmd, boolean isNew) {
        if (cmd.model() == CarBrand.CarModel.SONSTIGE_CUSTOM) {
            throw new ValidationException("Sonstige bekommt keine Spec-Zeilen. Fehlende Modelle kommen ins Enum.");
        }
        if (cmd.netBatteryCapacityKwh().compareTo(cmd.batteryCapacityKwh()) > 0) {
            throw new ValidationException("Netto darf nicht größer als Brutto sein.");
        }
        if (isNew && sanitize(cmd.trimLevel()).isEmpty()) {
            throw new ValidationException("Trim ist für neue Zeilen Pflicht (z. B. Long Range, Pro, GTX).");
        }
        if (isNew && cmd.availableFrom() == null) {
            throw new ValidationException("Verfügbar ab ist für neue Zeilen Pflicht.");
        }
        if (cmd.availableFrom() != null && cmd.availableTo() != null && !cmd.availableTo().isAfter(cmd.availableFrom())) {
            throw new ValidationException("Verfügbar bis muss nach Verfügbar ab liegen.");
        }
        requireHttps(cmd.sourceUrl());
        requireDcPower(cmd.dcMaxPowerKw(), cmd.dcAvgPower1080Kw());
    }

    /** Beide Werte optional, je 1-1000 kW; der Mittelwert 10-80 % kann nicht über dem Maximum liegen. */
    static void requireDcPower(BigDecimal max, BigDecimal avg) {
        requireKwRange(max, "DC max");
        requireKwRange(avg, "DC 10-80 %");
        if (max != null && avg != null && avg.compareTo(max) > 0) {
            throw new ValidationException("DC 10-80 % darf nicht über DC max liegen.");
        }
    }

    private static void requireKwRange(BigDecimal value, String label) {
        if (value != null && (value.compareTo(BigDecimal.ONE) < 0 || value.compareTo(MAX_DC_KW) > 0)) {
            throw new ValidationException(label + " muss zwischen 1 und 1000 kW liegen.");
        }
    }

    static void requireHttps(String url) {
        if (url == null || url.isBlank()) {
            throw new ValidationException("Quelle ist Pflicht (https-Link, z. B. ev-database.org).");
        }
        try {
            URI uri = URI.create(url.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new ValidationException("Quelle muss ein https-Link sein.");
            }
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Quelle muss ein https-Link sein.");
        }
    }

    static BigDecimal ratio(VehicleSpecification spec) {
        if (spec.getNetBatteryCapacityKwh() == null || spec.getNetBatteryCapacityKwh().signum() == 0) return null;
        return spec.getOfficialRangeKm().multiply(spec.getOfficialConsumptionKwhPer100km())
                .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
                .divide(spec.getNetBatteryCapacityKwh(), 2, RoundingMode.HALF_UP);
    }

    /** Warnungen für eine Zeile gegen die übrigen Zeilen desselben Modells. */
    static List<String> warnings(VehicleSpecification spec, List<VehicleSpecification> siblings) {
        List<String> warnings = new ArrayList<>();
        BigDecimal ratio = ratio(spec);
        if (ratio != null && ratio.compareTo(MIN_RATIO) < 0) {
            warnings.add("Verhältnis WLTP-km × Verbrauch zu Netto ist " + ratio.toPlainString().replace('.', ',')
                    + ", unter 1,10: Realwert statt WLTP eingetragen?");
        }
        if (USER_ROW_NAME.matcher(spec.getVariantName() == null ? "" : spec.getVariantName()).matches()) {
            warnings.add("Name sieht aus wie eine Nutzer-Zeile (\"Brutto/Netto kWh\").");
        }
        String trim = sanitize(spec.getTrimLevel());
        if (!trim.isEmpty()) {
            siblings.stream()
                    .filter(o -> !o.getId().equals(spec.getId()))
                    .filter(o -> trim.equalsIgnoreCase(sanitize(o.getTrimLevel())))
                    .filter(o -> overlaps(spec, o))
                    .findFirst()
                    .ifPresent(o -> warnings.add("Zeitraum überlappt \"" + o.getVariantName() + "\" (gleicher Trim)."));
        }
        return warnings;
    }

    private static boolean overlaps(VehicleSpecification a, VehicleSpecification b) {
        LocalDate aFrom = a.getAvailableFrom() == null ? LocalDate.MIN : a.getAvailableFrom();
        LocalDate aTo = a.getAvailableTo() == null ? LocalDate.MAX : a.getAvailableTo();
        LocalDate bFrom = b.getAvailableFrom() == null ? LocalDate.MIN : b.getAvailableFrom();
        LocalDate bTo = b.getAvailableTo() == null ? LocalDate.MAX : b.getAvailableTo();
        return !aFrom.isAfter(bTo) && !bFrom.isAfter(aTo);
    }

    /** Spiegelt uq_vehicle_spec (V206): die Generation unterscheidet allein der Start des Zeitraums, nicht der Name. */
    static boolean sameUniqueKey(VehicleSpecification a, VehicleSpecification b) {
        return a.getCarBrand().equals(b.getCarBrand())
                && a.getCarModel().equals(b.getCarModel())
                && a.getBatteryCapacityKwh().compareTo(b.getBatteryCapacityKwh()) == 0
                && sanitize(a.getVariantName()).equals(sanitize(b.getVariantName()))
                && a.getWltpType() == b.getWltpType()
                && a.getRatingSource() == b.getRatingSource()
                && Objects.equals(a.getAvailableFrom(), b.getAvailableFrom());
    }
}
