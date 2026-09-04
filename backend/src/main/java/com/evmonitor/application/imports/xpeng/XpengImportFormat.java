package com.evmonitor.application.imports.xpeng;

/**
 * Transportformat eines XPeng-Uploads. Beide Formate liefern dieselben
 * {@code XpengTelematicsRow}-Signale und laufen ab dem Parser durch dieselben
 * Trip-/Charge-Detektoren - sie unterscheiden sich nur in Upload-Validierung
 * (Magic Bytes) und Parser.
 *
 * <ul>
 *   <li>{@link #XLSX} - altes Format: (ggf. verschluesselte) Excel-Datei, per Mail verteilt.</li>
 *   <li>{@link #CSV_ZIP} - neues EU-Data-Act-Format (ab 09/2026): ZIP mit unverschluesselten
 *       CSV-Clustern, per API abgerufen.</li>
 * </ul>
 */
public enum XpengImportFormat {
    XLSX,
    CSV_ZIP
}
