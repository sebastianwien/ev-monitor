package com.evmonitor.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * R15-Herleitung des verpassten Start-SoC einer Smartcar-Ladung. Hat Smartcar den Ladebeginn
 * nicht gemeldet, ist der Zaehler (kWh) die Wahrheit und der End-SoC gueltig, aber der Start-SoC
 * unbekannt. Rueckgerechnet ueber den kWh-Bedarf pro SoC-Prozentpunkt:
 * <ul>
 *   <li>primaer aus den vollstaendigen Ladungen desselben Autos (median kWh/Punkt) - das faengt
 *       echte Kapazitaet + Ladeverluste ohne SoH-Modell,</li>
 *   <li>Fallback: Nominalkapazitaet / 100.</li>
 * </ul>
 * Pure Rechnung, kein DB-/Framework-Zugriff - der Aufrufer liefert die sauberen Ladungen.
 */
public final class MissedStartSocEstimator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    /** Ein SoC-Punkt entspricht Kapazitaet/100; fuer 20-150 kWh-Akkus 0.2-1.5 kWh/Punkt. */
    private static final BigDecimal MIN_KWH_PER_POINT = new BigDecimal("0.2");
    private static final BigDecimal MAX_KWH_PER_POINT = new BigDecimal("1.5");
    /** Nur aussagekraeftige Fenster als Kalibrier-Basis (kleine Fenster verrauschen). */
    private static final BigDecimal MIN_SOC_DELTA = BigDecimal.TEN;

    private MissedStartSocEstimator() {}

    /** Eine vollstaendige Ladung des Autos als Kalibrier-Punkt. */
    public record Charge(BigDecimal socBefore, BigDecimal socAfter, BigDecimal kwh) {}

    /**
     * @return geschaetzter Start-SoC (ganze Prozent, geklemmt auf [0, socAfter]) oder null,
     *         wenn weder saubere Ladungen noch Nominalkapazitaet eine Basis liefern.
     */
    public static BigDecimal estimateSocStart(BigDecimal socAfter, BigDecimal kwh,
                                              List<Charge> cleanCharges, BigDecimal nominalCapacityKwh) {
        if (socAfter == null || kwh == null || kwh.signum() <= 0) return null;

        BigDecimal kwhPerPoint = medianKwhPerPoint(cleanCharges);
        if (kwhPerPoint == null && nominalCapacityKwh != null && nominalCapacityKwh.signum() > 0) {
            kwhPerPoint = nominalCapacityKwh.divide(HUNDRED, 4, RoundingMode.HALF_UP);
        }
        if (kwhPerPoint == null || kwhPerPoint.signum() <= 0) return null;

        BigDecimal deltaPoints = kwh.divide(kwhPerPoint, 0, RoundingMode.HALF_UP);
        BigDecimal socStart = socAfter.subtract(deltaPoints);
        if (socStart.signum() < 0) return BigDecimal.ZERO;
        if (socStart.compareTo(socAfter) > 0) return socAfter;
        return socStart;
    }

    private static BigDecimal medianKwhPerPoint(List<Charge> charges) {
        if (charges == null || charges.isEmpty()) return null;
        List<BigDecimal> ratios = new ArrayList<>();
        for (Charge c : charges) {
            if (c.socBefore() == null || c.socAfter() == null || c.kwh() == null) continue;
            BigDecimal delta = c.socAfter().subtract(c.socBefore());
            if (delta.compareTo(MIN_SOC_DELTA) < 0 || c.kwh().signum() <= 0) continue;
            BigDecimal perPoint = c.kwh().divide(delta, 4, RoundingMode.HALF_UP);
            if (perPoint.compareTo(MIN_KWH_PER_POINT) >= 0 && perPoint.compareTo(MAX_KWH_PER_POINT) <= 0) {
                ratios.add(perPoint);
            }
        }
        if (ratios.isEmpty()) return null;
        ratios.sort(BigDecimal::compareTo);
        int n = ratios.size();
        if (n % 2 == 1) return ratios.get(n / 2);
        return ratios.get(n / 2 - 1).add(ratios.get(n / 2)).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }
}
