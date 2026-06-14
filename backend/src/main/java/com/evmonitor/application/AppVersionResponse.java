package com.evmonitor.application;

/**
 * Versions-Metadaten der nativen App (Capacitor), die der Client beim Start abfragt.
 *
 * @param minNativeVersion Niedrigste noch unterstuetzte Native-Version (Versionscode). Ist die
 *                         installierte App aelter, zeigt sie einen "Im Store aktualisieren"-Hinweis.
 *                         Web-Bundle-Updates laufen ueber Capgo, nicht ueber dieses Feld.
 */
public record AppVersionResponse(int minNativeVersion) {
}
