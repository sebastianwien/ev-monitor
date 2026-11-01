package com.evmonitor.domain;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Faktoren der CO₂-Bilanz, beide Seiten inklusive Vorkette (Well-to-Wheel) und ortsbasiert:
 * jede geladene kWh zählt mit dem deutschen Strommix, auch Ökostromtarife. Quellen und
 * Grenzen in docs/features/co2.md. Einzige Stelle für diese Werte.
 */
public final class Co2Factors {

    /**
     * Benzin, kg CO₂-Äquivalente je Liter: fossiler Vergleichswert der Richtlinie (EU) 2018/2001
     * (RED II, 94 g CO₂e/MJ) mal Energiegehalt von Ottokraftstoff (32 MJ/l, Anhang III).
     */
    public static final double PETROL_KG_CO2E_PER_LITER = 94.0 * 32.0 / 1000.0;

    /**
     * Deutscher Strommix, g CO₂-Äquivalente je kWh Stromverbrauch mit Vorketten:
     * UBA Climate Change 16/2026, Tabelle 3, Datenstand Februar 2026 (2025 geschätzt).
     */
    private static final NavigableMap<Integer, Integer> GRID_G_CO2E_PER_KWH = new TreeMap<>(Map.of(
            2020, 435,
            2021, 477,
            2022, 503,
            2023, 442,
            2024, 414,
            2025, 406));

    private Co2Factors() {}

    /** Strommix des Jahres; vor der Reihe gilt das erste Jahr, danach das neueste. */
    public static int gridGramsPerKwh(int year) {
        if (year < GRID_G_CO2E_PER_KWH.firstKey()) return GRID_G_CO2E_PER_KWH.firstEntry().getValue();
        return GRID_G_CO2E_PER_KWH.floorEntry(year).getValue();
    }
}
