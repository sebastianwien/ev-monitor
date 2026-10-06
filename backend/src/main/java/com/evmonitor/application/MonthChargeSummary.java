package com.evmonitor.application;

import java.math.BigDecimal;
import java.util.UUID;

/** Monatssumme des Autos, mit dem der Nutzer im Zeitraum am häufigsten geladen hat. */
public record MonthChargeSummary(UUID carId, long charges, long homeCharges, BigDecimal kwh) {
}
