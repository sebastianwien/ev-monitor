package com.evmonitor.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Verweis des Clients auf eine Register-Saeule: nur Name und Zelle. Alles Weitere
 * (Leistung, Ladepunkte) holt der Server selbst aus dem Register - siehe {@link ChargingSiteService}.
 */
public record ChargingSiteRef(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "[0-9b-hjkmnp-z]{7}") String geohash) {}
