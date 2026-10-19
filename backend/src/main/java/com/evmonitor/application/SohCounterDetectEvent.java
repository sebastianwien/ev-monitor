package com.evmonitor.application;

import java.util.UUID;

/** Eine Fahrt mit Fahrzeug-Energiezähler wurde gespeichert: SoH-Quelle VEHICLE_COUNTER nachrechnen. */
public record SohCounterDetectEvent(UUID carId) {}
