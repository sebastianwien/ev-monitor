package com.evmonitor.application.imports.vweuda;

/**
 * Die Datei selbst ist nicht verarbeitbar - unbekanntes Format, kaputtes ZIP, zu gross.
 * <p>
 * Abgegrenzt von den uebrigen fachlichen Fehlern (unbekanntes Fahrzeug, fehlende Rechte):
 * nur bei dieser Ausnahme darf der AutoSync-Connector einen Portal-Datensatz dauerhaft
 * ueberspringen, weil ein erneuter Versuch identisch scheitern wuerde. Erbt von
 * {@link IllegalArgumentException}, damit der manuelle Upload sie unveraendert als 400
 * mit Originalmeldung ausliefert.
 */
public class VwEudaUnreadableException extends IllegalArgumentException {

    public VwEudaUnreadableException(String message) {
        super(message);
    }

    public VwEudaUnreadableException(String message, Throwable cause) {
        super(message, cause);
    }
}
