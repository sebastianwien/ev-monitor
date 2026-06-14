package com.evmonitor.application;

/**
 * Versions-Metadaten der nativen App (Capacitor), die der Client beim Start abfragt.
 *
 * @param webBuildHash     Git-SHA des aktuell ausgelieferten Web-Builds. Weicht er vom
 *                         lokal installierten Bundle ab, kann die App ein Live-Update ziehen.
 * @param minNativeVersion Niedrigste noch unterstuetzte Native-Version (Versionscode). Ist die
 *                         installierte App aelter, zeigt sie einen "Im Store aktualisieren"-Hinweis.
 */
public record AppVersionResponse(String webBuildHash, int minNativeVersion) {
}
