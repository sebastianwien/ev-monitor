package com.evmonitor.infrastructure.external.xpeng;

/**
 * Ergebnis eines {@code queryData}-Aufrufs im Two-Step-Polling.
 *
 * <ul>
 *   <li>{@link Status#EXPORTING} - Export laeuft noch, erneut pollen (5-10s spaeter).</li>
 *   <li>{@link Status#READY} - {@link #downloadUrl()} gesetzt (nur ~30s gueltig).</li>
 *   <li>{@link Status#FAILED} - Export fehlgeschlagen, kein Retry auf gleichem Stand.</li>
 * </ul>
 */
public record XpengQueryResult(Status status, String downloadUrl) {

    public enum Status {
        EXPORTING,
        READY,
        FAILED
    }

    public static XpengQueryResult exporting() {
        return new XpengQueryResult(Status.EXPORTING, null);
    }

    public static XpengQueryResult ready(String downloadUrl) {
        return new XpengQueryResult(Status.READY, downloadUrl);
    }

    public static XpengQueryResult failed() {
        return new XpengQueryResult(Status.FAILED, null);
    }
}
