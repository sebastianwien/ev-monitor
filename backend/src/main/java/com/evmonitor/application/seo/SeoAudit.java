package com.evmonitor.application.seo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** Typen des Unter-Tabs "Technik": Befunde je URL aus dem nächtlichen Crawl, Lese-Sicht für den Tab. */
public final class SeoAudit {

    private SeoAudit() {
    }

    public enum Check { STATUS, NOINDEX, CANONICAL, TITLE, DESCRIPTION, H1, JSONLD, PRERENDER, BROKEN_LINK, ORPHAN, CWV }

    public enum Severity { ERROR, WARNING }

    /** message ist für den Admin lesbar und enthält Seiteninhalte (Title, Linkziel): im Frontend nur als Text. */
    public record Finding(Check check, Severity severity, String message) {
    }

    /** Ergebnis einer gecrawlten Seite; links = normalisierte interne Seitenpfade ohne die Seite selbst. */
    public record PageReport(String path, int status, boolean noindex, List<Finding> findings, Set<String> links) {
    }

    /** Core Web Vitals einer Seite; source FIELD (echte Nutzer) oder LAB (Lighthouse, ohne INP). */
    public record Vitals(Integer lcpMs, Double cls, Integer inpMs, String source) {
    }

    public record AuditRow(String path, int httpStatus, int score, int errors, int warnings, List<Finding> findings,
                           Vitals vitals) {
    }

    /** Kacheln: geprüfte URLs, URLs mit Fehler, Seiten mit Prerender-Fehler, Seiten mit kaputtem Link, verwaiste Seiten. */
    public record Summary(int checked, int withErrors, int prerenderFailed, int brokenLinkPages, int orphans) {
    }

    /** day = Prüftag des letzten Laufs, null ohne Lauf; vitalsWeek analog. */
    public record View(boolean enabled, LocalDate day, LocalDateTime lastRunAt, LocalDate vitalsWeek, Summary summary,
                       List<AuditRow> rows) {
    }

    static final int ERROR_PENALTY = 20;
    static final int WARNING_PENALTY = 5;

    /** 100 minus 20 je Fehler minus 5 je Hinweis, nie unter 0. Nur zum Sortieren. */
    public static int score(List<Finding> findings) {
        int s = 100;
        for (Finding f : findings) s -= f.severity() == Severity.ERROR ? ERROR_PENALTY : WARNING_PENALTY;
        return Math.max(0, s);
    }
}
