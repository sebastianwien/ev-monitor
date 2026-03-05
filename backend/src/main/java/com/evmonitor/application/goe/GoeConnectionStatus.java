package com.evmonitor.application.goe;

import java.util.UUID;

public record GoeConnectionStatus(
        UUID id,
        String serial,
        String displayName,
        boolean active,
        int carState,
        String lastPollError
) {
    public String carStateLabel() {
        return switch (carState) {
            case 1 -> "Bereit";
            case 2 -> "Ladevorgang aktiv";
            case 3 -> "Wartet auf Fahrzeug";
            case 4 -> "Ladevorgang abgeschlossen";
            case 5 -> "Fehler";
            default -> "Unbekannt";
        };
    }
}
