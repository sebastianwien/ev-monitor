package com.evmonitor.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Co2FactorsTest {

    @Test
    void benzinInklusiveVorketteAusRedTwo() {
        assertEquals(3.008, Co2Factors.PETROL_KG_CO2E_PER_LITER, 1e-9);
    }

    @Test
    void strommixJeJahrMitRandjahren() {
        assertEquals(435, Co2Factors.gridGramsPerKwh(2019));
        assertEquals(435, Co2Factors.gridGramsPerKwh(2020));
        assertEquals(503, Co2Factors.gridGramsPerKwh(2022));
        assertEquals(442, Co2Factors.gridGramsPerKwh(2023));
        assertEquals(406, Co2Factors.gridGramsPerKwh(2025));
        assertEquals(406, Co2Factors.gridGramsPerKwh(2026));
    }
}
